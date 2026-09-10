package org.example.tears.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.InpDTO.CreateRequestStepDto;
import org.example.tears.Model.*;
import org.example.tears.OutDTO.RequestResponseDto;
import org.example.tears.Repository.CarServiceRequestRepository;
import org.example.tears.Repository.PaymentIntentRepository;
import org.example.tears.Repository.RequestApprovalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final CarServiceRequestRepository requestRepository;
    @Lazy
    private final CarServiceRequestService carServiceRequestService;
    private final NotificationService notificationService;
    private final RequestApprovalRepository approvalRepo;
    private final AuthService authService;
    private final WalletService walletService;
    private final SocketService socketService;


    private final RestTemplate restTemplate = new RestTemplate();
    private final AppointmentService appointmentService;
    private final CouponService couponService;

    @Value("${MOYASAR_SECRET_KEY}")
    private String secretKey;

    @Value("${MOYASAR_CALLBACK_URL}")
    private String callbackUrl;

    @Value("${MOYASAR_SUCCESS_URL}")
    private String successUrl;

    @Value("${MOYASAR_BACK_URL}")
    private String backUrl;

    @Value("${moyasar.webhook.token}")
    private String webhookToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

   // انشاء فاتوره دفع خارجي لانشاء الطلب
    @Transactional
    public CheckoutResponse createCheckout(
            HttpServletRequest request,
            CreateRequestStepDto dto
    ) {
        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest draft =
                carServiceRequestService.buildValidatedRequest(user, dto);

        PaymentIntent intent = new PaymentIntent();

        intent.setCustomer(user.getCustomer());
        intent.setCar(draft.getCar());
        intent.setServiceOption(draft.getServiceOption());
        intent.setProblemDescription(draft.getProblemDescription());
        intent.setAppointmentDate(draft.getAppointmentDate());
        intent.setAppointmentTime(draft.getAppointmentTime());
        intent.setHydraulicTruck(draft.isHydraulicTruck());
        intent.setLocation(draft.getLocation());
        intent.setPaymentMethod(draft.getPaymentMethod());
        intent.setEstimatedPrice(draft.getEstimatedPrice());

        intent.setInitialPaymentAmount(draft.getEstimatedPrice());
        intent.setInitialPaymentAmountHalalah(
                (int) Math.round(draft.getEstimatedPrice() * 100)
        );
        intent.setInitialPaymentMethod(draft.getPaymentMethod());
        intent.setInitialPaymentStatus(PaymentStatus.INITIATED);
        intent.setEstimatedPrice(draft.getEstimatedPrice());
        intent.setOriginalPrice(draft.getOriginalPrice());
        intent.setDiscount(draft.getDiscount());
        intent.setVatAmount(draft.getVatAmount());
        intent.setCouponValid(draft.getCouponValid());
        intent.setPricingMessage(draft.getPricingMessage());

        intent.setPaymentStatus(PaymentStatus.INITIATED);
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        intent.setCreatedAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

        int amountHalalah =
                (int) Math.round(draft.getEstimatedPrice() * 100);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountHalalah);
        body.put("currency", "SAR");
        body.put("description", "Request #" + intent.getId());
        body.put("callback_url", callbackUrl);
        body.put("success_url", successUrl);
        body.put("back_url", backUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.moyasar.com/v1/invoices",
                new HttpEntity<>(body, headers),
                Map.class
        );

        Map data = response.getBody();

        if (data == null || data.get("id") == null || data.get("url") == null) {
            throw new ApiException("Invalid Moyasar invoice response");
        }

        String invoiceId = data.get("id").toString();
        String checkoutUrl = data.get("url").toString();

        intent.setInvoiceId(invoiceId);
        intent.setCheckoutUrl(checkoutUrl);

        paymentIntentRepository.save(intent);

        return new CheckoutResponse(
                intent.getId().toString(),
                invoiceId,
                checkoutUrl,
                amountHalalah
        );
    }

    //الدفع عن طريق المحفظه لانشاء الطلب
    @Transactional
    public RequestResponseDto payRequestWithWallet(
            HttpServletRequest request,
            CreateRequestStepDto dto
    ) {

        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest req =
                carServiceRequestService.buildValidatedRequest(user, dto);

        if (req.getEstimatedPrice() == null || req.getEstimatedPrice() <= 0) {
            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        int amountHalalah =
                (int) Math.round(req.getEstimatedPrice() * 100);

        walletService.payFromWallet(
                user,
                amountHalalah,
                "REQ-" + UUID.randomUUID().toString().substring(0, 8)
        );

        // من هنا فقط نعتبر الدفع ناجح
        req.setPaymentMethod(PaymentMethod.WALLET);
        req.setInitialPaid(true);
        req.setInitialPaymentMethod(PaymentMethod.WALLET);
        req.setInitialPaymentStatus(PaymentStatus.PAID);
        req.setInitialPaymentAmount(req.getEstimatedPrice());
        req.setInitialPaymentAmountHalalah(amountHalalah);
        req.setRemainingAmount(0.0);

        User customerUser = req.getCustomer().getUser();

        if (customerUser.getTestTechnician() != null) {

            req.setAssignedTechnician(
                    customerUser.getTestTechnician()
            );
        }
        req.setCustomerStatus(CustomerRequestStatus.REQUEST_CREATED);
        req.setStaffStatus(StaffRequestStatus.NEW);
        req.setStage(WorkflowStage.NEW_REQUEST);

        req.setLastUpdated(LocalDateTime.now());
        req.setCreatedAt(LocalDateTime.now());

        CarServiceRequest savedRequest =
                requestRepository.save(req);

        savedRequest.setOrderNumber(
                String.format("ORD-%06d", savedRequest.getId())
        );

        requestRepository.save(savedRequest);

        socketService.send(
                "/topic/current-orders/" +
                        req.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(req)
        );

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        notificationService.send(

                savedRequest.getCustomer().getUser(),

                NotificationType.REQUEST_CREATED,
                NotificationCategory.REQUEST,

                "تم إنشاء طلبك",

                "تم إنشاء طلبك #"
                        + savedRequest.getOrderNumber()
                        + " بنجاح.",

                NotificationActionType.OPEN_ENTITY,

                NotificationEntityType.REQUEST,

                savedRequest.getId().toString(),

                NotificationSection.REQUESTS
        );

        return carServiceRequestService.toResponseDto(savedRequest);
    }

    @Transactional
    public RequestResponseDto payFinalWithWallet(
            Integer requestId,
            HttpServletRequest httpRequest
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        CarServiceRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        if (!request.getCustomer().getId().equals(user.getCustomer().getId())) {
            throw new ApiException("غير مصرح لك");
        }

        RequestApproval approval =
                approvalRepo.findByRequest_Id(requestId)
                        .orElseThrow(() ->
                                new ApiException("لا يوجد تقرير"));

        if (Boolean.TRUE.equals(approval.getApproved())) {
            throw new ApiException("تمت الموافقة مسبقاً");
        }

        if (request.isFinalPaid()) {
            throw new ApiException("تم السداد مسبقاً");
        }

        if (request.getFinalPrice() == null || request.getFinalPrice() <= 0) {
            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        int amountHalalah =
                (int) Math.round(request.getFinalPrice() * 100);

        walletService.payFromWallet(
                user,
                amountHalalah,
                "FINAL-" + request.getOrderNumber()
        );

        approval.setApproved(true);
        approval.setDecisionAt(LocalDateTime.now());

        approvalRepo.save(approval);

        request.setFinalPaid(true);

        request.setNextPaymentMethod(PaymentMethod.WALLET);

        request.setNextPaymentStatus(PaymentStatus.PAID);

        request.setCustomerStatus(CustomerRequestStatus.UNDER_REPAIR);

        request.setStaffStatus(StaffRequestStatus.REPAIRING);

        request.setStage(WorkflowStage.REPAIRING);

        request.setRepairAt(LocalDateTime.now());

        request.setLastUpdated(LocalDateTime.now());

        requestRepository.save(request);
        socketService.send(
                "/topic/current-orders/" + request.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(request)
        );
        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        if (request.getCurrentEmployee() != null) {
            notificationService.send(
                    request.getCurrentEmployee().getUser(),

                    NotificationType.PAYMENT_SUCCEEDED,
                    NotificationCategory.PAYMENT,

                    "تم سداد الدفعة النهائية",

                    "تم سداد الدفعة النهائية للطلب #"
                            + request.getOrderNumber(),

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.REQUEST,
                    request.getId().toString(),
                    NotificationSection.REQUESTS
            );

        }

        return carServiceRequestService.toResponseDto(request);
    }

    // الرد على فاتوره الدفع الخارجي لانشاء الطلب
    @Transactional
    public RequestResponseDto handleInvoiceCallback(Map<String, Object> payload) {

        String invoiceId = payload.get("id").toString();
        String status = payload.get("status").toString();

        PaymentIntent intent = paymentIntentRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ApiException("Payment intent not found"));

        if (!"paid".equalsIgnoreCase(status)) {
            intent.setPaymentStatus(PaymentStatus.FAILED);
            paymentIntentRepository.save(intent);
            throw new ApiException("Payment failed");
        }

        if (intent.getServiceRequest() != null) {
            return carServiceRequestService.toResponseDto(intent.getServiceRequest());
        }

        if (intent.getExpiresAt() != null
                && intent.getExpiresAt().isBefore(LocalDateTime.now())) {

            intent.setPaymentStatus(PaymentStatus.EXPIRED);
            paymentIntentRepository.save(intent);

            throw new ApiException("Payment intent expired");
        }

        CarServiceRequest req = new CarServiceRequest();

        req.setCustomer(intent.getCustomer());
        req.setCar(intent.getCar());
        req.setServiceOption(intent.getServiceOption());
        req.setProblemDescription(intent.getProblemDescription());
        req.setAppointmentDate(intent.getAppointmentDate());
        req.setAppointmentTime(intent.getAppointmentTime());
        req.setHydraulicTruck(intent.getHydraulicTruck());
        req.setLocation(intent.getLocation());

        req.setPaymentMethod(intent.getPaymentMethod());
        req.setInitialPaid(true);
        req.setInitialPaymentMethod(intent.getPaymentMethod());
        req.setInitialPaymentStatus(PaymentStatus.PAID);
        req.setInitialPaymentAmount(intent.getEstimatedPrice());
        req.setInitialPaymentAmountHalalah(
                (int) Math.round(intent.getEstimatedPrice() * 100)
        );
        req.setRemainingAmount(0.0);
        req.setEstimatedPrice(intent.getEstimatedPrice());
        req.setOriginalPrice(intent.getOriginalPrice());
        req.setDiscount(intent.getDiscount());
        req.setVatAmount(intent.getVatAmount());
        req.setCouponValid(intent.getCouponValid());
        req.setPricingMessage(intent.getPricingMessage());
        User customerUser = req.getCustomer().getUser();

        if (customerUser.getTestTechnician() != null) {

            req.setAssignedTechnician(
                    customerUser.getTestTechnician()
            );
        }
        req.setCustomerStatus(CustomerRequestStatus.REQUEST_CREATED);
        req.setStaffStatus(StaffRequestStatus.NEW);
        req.setStage(WorkflowStage.NEW_REQUEST);

        req.setLastUpdated(LocalDateTime.now());
        req.setCreatedAt(LocalDateTime.now());

        CarServiceRequest savedRequest = requestRepository.save(req);
        String paymentId =
                resolveInvoicePaymentId(invoiceId);

        savedRequest.setInitialTransactionId(paymentId);
        savedRequest.setPaymentId(paymentId);
        requestRepository.save(savedRequest);
        intent.setServiceRequest(savedRequest);
        intent.setPaymentStatus(PaymentStatus.PAID);
        intent.setPaymentId(paymentId);
        intent.setPaidAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

        socketService.send(
                "/topic/current-orders/"
                        + savedRequest.getCustomer()
                        .getUser()
                        .getId(),

                carServiceRequestService.toCurrentDto(
                        savedRequest
                )
        );

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        notificationService.send(

                savedRequest.getCustomer().getUser(),

                NotificationType.REQUEST_CREATED,
                NotificationCategory.REQUEST,

                "تم إنشاء طلبك",

                "تم إنشاء طلبك #"
                        + savedRequest.getOrderNumber()
                        + " بنجاح.",

                NotificationActionType.OPEN_ENTITY,

                NotificationEntityType.REQUEST,

                savedRequest.getId().toString(),

                NotificationSection.REQUESTS
        );

        return carServiceRequestService.toResponseDto(savedRequest);
    }

    @Transactional
    public RequestResponseDto getPaymentResult(Integer paymentIntentId) {

        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ApiException("Payment intent not found"));

        if (intent.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ApiException("Payment is not completed yet");
        }

        if (intent.getServiceRequest() == null) {
            throw new ApiException("Request was not created yet");
        }

        return carServiceRequestService.toResponseDto(intent.getServiceRequest());
    }

    //انشاء فاتوره الدفع للتقرير
    @Transactional
    public CheckoutResponse createFinalCheckout(
            Integer requestId,
            HttpServletRequest httpRequest
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        CarServiceRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        if (!request.getCustomer().getId().equals(user.getCustomer().getId())) {
            throw new ApiException("غير مصرح لك");
        }

        RequestApproval approval =
                approvalRepo.findByRequest_Id(request.getId())
                        .orElseThrow(() ->
                                new ApiException("Approval not found"));
        if (Boolean.TRUE.equals(approval.getApproved())) {
            throw new ApiException("تمت الموافقة مسبقاً");
        }

        if (request.isFinalPaid()) {
            throw new ApiException("تم سداد الدفعة النهائية مسبقاً");
        }

        if (request.getFinalPrice() == null || request.getFinalPrice() <= 0) {
            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        PaymentIntent intent = new PaymentIntent();

        approval.setApproved(true);
        approval.setDecisionAt(LocalDateTime.now());

        approvalRepo.save(approval);

        request.setFinalPaid(true);

        request.setNextPaymentMethod(intent.getPaymentMethod());

        request.setNextPaymentStatus(PaymentStatus.PAID);

        request.setCustomerStatus(CustomerRequestStatus.UNDER_REPAIR);

        request.setStaffStatus(StaffRequestStatus.REPAIRING);

        request.setStage(WorkflowStage.REPAIRING);

        request.setRepairAt(LocalDateTime.now());

        request.setLastUpdated(LocalDateTime.now());

        requestRepository.save(request);




        intent.setCustomer(user.getCustomer());
        intent.setServiceRequest(request);

        intent.setInitialPaymentAmount(request.getFinalPrice().doubleValue());

        intent.setInitialPaymentAmountHalalah(
                request.getFinalPrice() * 100
        );

        intent.setPaymentMethod(request.getPaymentMethod());

        intent.setPaymentStatus(PaymentStatus.PAID);

        intent.setCreatedAt(LocalDateTime.now());

        intent.setPaidAt(LocalDateTime.now());

        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        paymentIntentRepository.save(intent);

        int amountHalalah = request.getFinalPrice() * 100;

        Map<String, Object> body = new HashMap<>();

        body.put("amount", amountHalalah);
        body.put("currency", "SAR");
        body.put("description",
                "Final Payment - Request #" + request.getOrderNumber());

        body.put("callback_url", callbackUrl);
        body.put("success_url", successUrl);
        body.put("back_url", backUrl);

        HttpHeaders headers = new HttpHeaders();

        headers.setBasicAuth(secretKey, "");

        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "https://api.moyasar.com/v1/invoices",
                        new HttpEntity<>(body, headers),
                        Map.class
                );

        Map data = response.getBody();

        if (data == null || data.get("id") == null || data.get("url") == null) {
            throw new ApiException("Invalid Moyasar invoice response");
        }

        intent.setInvoiceId(data.get("id").toString());

        intent.setCheckoutUrl(data.get("url").toString());

        paymentIntentRepository.save(intent);

        return new CheckoutResponse(
                intent.getId().toString(),
                intent.getInvoiceId(),
                intent.getCheckoutUrl(),
                amountHalalah
        );
    }


   // الرد على فاتورة الدفع للتقرير
    @Transactional
    public RequestResponseDto handleFinalInvoiceCallback(
            Map<String, Object> payload
    ) {

        String invoiceId = payload.get("id").toString();
        String status = payload.get("status").toString();

        PaymentIntent intent = paymentIntentRepository
                .findByInvoiceId(invoiceId)
                .orElseThrow(() ->
                        new ApiException("Payment intent not found"));

        if (!"paid".equalsIgnoreCase(status)) {

            intent.setPaymentStatus(PaymentStatus.FAILED);

            paymentIntentRepository.save(intent);

            throw new ApiException("Payment failed");
        }

        CarServiceRequest request = intent.getServiceRequest();

        if (request == null) {
            throw new ApiException("Request not found");
        }

        RequestApproval approval =
                approvalRepo.findByRequest_Id(request.getId())
                        .orElseThrow(() ->
                                new ApiException("Approval not found"));

        approval.setApproved(true);
        approval.setDecisionAt(LocalDateTime.now());

        approvalRepo.save(approval);

        if (request == null) {
            throw new ApiException("Request not found");
        }

        // تحديث بيانات الدفع
        request.setFinalPaid(true);
        request.setFinalTransactionId(invoiceId);

        request.setNextPaymentMethod(intent.getPaymentMethod());
        request.setNextPaymentStatus(PaymentStatus.PAID);

        // تحديث حالة الطلب
        request.setCustomerStatus(CustomerRequestStatus.UNDER_REPAIR);
        request.setStaffStatus(StaffRequestStatus.REPAIRING);
        request.setStage(WorkflowStage.REPAIRING);

        request.setRepairAt(LocalDateTime.now());
        request.setLastUpdated(LocalDateTime.now());

        requestRepository.save(request);
        socketService.send(
                "/topic/current-orders/" + request.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(request)
        );

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        // تحديث عملية الدفع
        intent.setPaymentStatus(PaymentStatus.PAID);
        intent.setPaidAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

        // إشعار للفني
        if (request.getCurrentEmployee() != null) {
            notificationService.send(
                    request.getCurrentEmployee().getUser(),

                    NotificationType.PAYMENT_SUCCEEDED,
                    NotificationCategory.PAYMENT,

                    "تم سداد الدفعة النهائية",

                    "تم سداد الدفعة النهائية للطلب #"
                            + request.getOrderNumber(),

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.REQUEST,
                    request.getId().toString(),
                    NotificationSection.REQUESTS
            );
        }

        return carServiceRequestService.toResponseDto(request);
    }

    // الدفع النهائي
    private RequestResponseDto completePayment(
            PaymentIntent intent,
            String paymentId
    ) {
        intent = paymentIntentRepository
                .findByIdForUpdate(intent.getId())
                .orElseThrow(() ->
                        new ApiException("PaymentIntent not found"));

        if (intent.getPaymentStatus() != PaymentStatus.INITIATED) {
            if (intent.getPaymentStatus() == PaymentStatus.PAID
                    && intent.getPaymentId() != null
                    && intent.getPaymentId().equals(paymentId)) {

                return carServiceRequestService.toResponseDto(
                        intent.getServiceRequest()
                );
            }

            throw new ApiException("Payment is not valid for completion");
        }

        CarServiceRequest request = intent.getServiceRequest();

        if (request == null) {

            request = createRequestFromIntent(intent);

            request.setInitialTransactionId(paymentId);
            request.setPaymentId(paymentId);

            requestRepository.save(request);

            intent.setServiceRequest(request);
        } else {

            RequestApproval approval =
                    approvalRepo.findByRequest_Id(request.getId())
                            .orElseThrow(() ->
                                    new ApiException("Approval not found"));

            approval.setApproved(true);
            approval.setDecisionAt(LocalDateTime.now());

            approvalRepo.save(approval);

            request.setFinalPaid(true);
            request.setFinalTransactionId(paymentId);
            request.setNextPaymentMethod(intent.getPaymentMethod());
            request.setNextPaymentStatus(PaymentStatus.PAID);

            request.setCustomerStatus(
                    CustomerRequestStatus.UNDER_REPAIR
            );

            request.setStaffStatus(
                    StaffRequestStatus.REPAIRING
            );

            request.setStage(
                    WorkflowStage.REPAIRING
            );
            User customerUser = request.getCustomer().getUser();

            if (customerUser.getTestTechnician() != null) {

                request.setAssignedTechnician(
                        customerUser.getTestTechnician()
                );
            }

            request.setRepairAt(LocalDateTime.now());
            request.setLastUpdated(LocalDateTime.now());

            requestRepository.save(request);

            // ==============================
            // Coupon Usage
            // ==============================

            if (intent.getCouponCode() != null &&
                    !intent.getCouponCode().isBlank()) {

                Customer customer = request.getCustomer();

                ServiceOption option =
                        request.getServiceOption();

                Coupon coupon =
                        couponService.validate(
                                intent.getCouponCode(),
                                request.getEstimatedPrice(),
                                option,
                                customer
                        );

                couponService.recordCouponUsage(
                        coupon,
                        customer,
                        request
                );
            }

            socketService.send(
                    "/topic/current-orders/" +
                            request.getCustomer().getUser().getId(),
                    carServiceRequestService.toCurrentDto(request)
            );

            socketService.send(
                    "/topic/request/" + request.getId(),
                    carServiceRequestService.toDetailsDto(request)
            );

            socketService.send(
                    "/topic/availability",
                    appointmentService.getAllAvailability()
            );

            if (request.getCurrentEmployee() != null) {
                notificationService.send(
                        request.getCurrentEmployee().getUser(),

                        NotificationType.PAYMENT_SUCCEEDED,
                        NotificationCategory.PAYMENT,

                        "تم سداد الدفعة النهائية",

                        "تم سداد الدفعة النهائية للطلب #"
                                + request.getOrderNumber(),

                        NotificationActionType.OPEN_ENTITY,
                        NotificationEntityType.REQUEST,
                        request.getId().toString(),
                        NotificationSection.REQUESTS
                );
            }

            notificationService.send(
                    request.getCustomer().getUser(),

                    NotificationType.PAYMENT_SUCCEEDED,
                    NotificationCategory.PAYMENT,

                    "تم سداد الدفعة بنجاح",

                    "تم استلام الدفعة بنجاح، وتم تحويل الطلب إلى مرحلة الإصلاح.",

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.REQUEST,
                    request.getId().toString(),
                    NotificationSection.REQUESTS
            );
        }

        intent.setPaymentStatus(PaymentStatus.PAID);

        intent.setPaymentId(paymentId);
        intent.setServiceRequest(request);
        intent.setPaidAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

        return carServiceRequestService.toResponseDto(request);
    }


     // انشاء دفعه داخل التطبيق
    @Transactional
    public MobilePaymentResponse prepareMobilePayment(
            HttpServletRequest request,
            CreateRequestStepDto dto
    ) {
        

        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest draft =
                carServiceRequestService.buildValidatedRequest(user, dto);

        PaymentIntent intent = new PaymentIntent();

        intent.setCustomer(user.getCustomer());
        intent.setCar(draft.getCar());
        intent.setServiceOption(draft.getServiceOption());
        intent.setProblemDescription(draft.getProblemDescription());
        intent.setAppointmentDate(draft.getAppointmentDate());
        intent.setAppointmentTime(draft.getAppointmentTime());
        intent.setHydraulicTruck(draft.isHydraulicTruck());
        intent.setLocation(draft.getLocation());
        intent.setType(PaymentIntentType.REQUEST);

        intent.setPaymentMethod(draft.getPaymentMethod());

        intent.setEstimatedPrice(draft.getEstimatedPrice());
        intent.setOriginalPrice(draft.getOriginalPrice());
        intent.setDiscount(draft.getDiscount());
        intent.setVatAmount(draft.getVatAmount());
        intent.setCouponValid(draft.getCouponValid());
        intent.setPricingMessage(draft.getPricingMessage());

        intent.setInitialPaymentAmount(draft.getEstimatedPrice());
        intent.setInitialPaymentAmountHalalah(
                (int)Math.round(draft.getEstimatedPrice()*100)
        );

        intent.setInitialPaymentMethod(draft.getPaymentMethod());

        intent.setPaymentStatus(PaymentStatus.INITIATED);

        intent.setCreatedAt(LocalDateTime.now());

        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        String givenId = UUID.randomUUID().toString();

        intent.setGivenId(givenId);

        paymentIntentRepository.save(intent);

        log.info(
                "Prepared mobile payment {}, givenId={}",
                intent.getId(),
                intent.getGivenId()
        );
        int amountHalalah =
                (int) Math.round(intent.getEstimatedPrice() * 100);

        return new MobilePaymentResponse(

                intent.getId(),

                intent.getId(),

                givenId,

                amountHalalah,

                "SAR",

                "Payment for order #" + intent.getId(),

                "PREPARED"
        );
    }

   // الرد على الدفعه داخل التطبيق
    @Transactional
    public ConfirmMobilePaymentResponse confirmMobilePayment(
            ConfirmMobilePaymentRequest dto
    ) {


        PaymentIntent intent =
                paymentIntentRepository
                        .findById(dto.getPaymentAttemptId())
                        .orElseThrow(() ->
                                new ApiException("Payment attempt not found"));


        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "https://api.moyasar.com/v1/payments/" + dto.getPaymentId(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

        Map<String, Object> payment = response.getBody();
        log.info("MOYASAR PAYMENT RESPONSE: {}", payment);

        if (payment == null)
            throw new ApiException("Payment not found");

        String status =
                payment.get("status").toString();

        if (!"paid".equalsIgnoreCase(status))
            throw new ApiException("Payment not completed");

        Integer amount =
                ((Number) payment.get("amount")).intValue();

        if (!amount.equals(intent.getInitialPaymentAmountHalalah()))
            throw new ApiException("Amount mismatch");

        if (!payment.get("currency").toString().equals("SAR"))
            throw new ApiException("Currency mismatch");

        Object metaObj = payment.get("metadata");

        if (!(metaObj instanceof Map<?, ?> metadata)) {
            throw new ApiException("Metadata missing");
        }


        String givenId =
                metadata.get("givenId").toString();

        if (!givenId.equals(intent.getGivenId()))
            throw new ApiException("GivenId mismatch");


        RequestResponseDto request =
                completePayment(intent, dto.getPaymentId());

        CarServiceRequest serviceRequest = intent.getServiceRequest();

        String carInfo =
                formatArabicPlate(serviceRequest.getCar().getPlateNumberArabic())
                        + "\n"
                        + formatEnglishPlate(serviceRequest.getCar().getPlateNumberEnglish());
        String appointmentDate =
                serviceRequest.getAppointmentDate()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new ConfirmMobilePaymentResponse(

                request.getId(),

                request.getOrderNumber(),

                intent.getId(),

                request.getStatus(),

                serviceRequest.getServiceOption().getDisplayName(),

                serviceRequest.getLocation().getAddress(),

                appointmentDate,

                carInfo,

                formatArabicPlate(serviceRequest.getCar().getPlateNumberArabic()),

                formatEnglishPlate(serviceRequest.getCar().getPlateNumberEnglish()),

                serviceRequest.getEstimatedPrice(),

                intent.getId(),

                dto.getPaymentId(),

                PaymentStatus.PAID.name()
        );
    }

    // wephook
    @Transactional
    public void handleWebhook(
            String token,
            String body
    ) {

        if (!webhookToken.equals(token)) {
            throw new ApiException("Invalid webhook token");
        }

        try {
            log.info("========== MOYASAR WEBHOOK ==========");
            log.info(body);

            JsonNode root = objectMapper.readTree(body);

            JsonNode data = root;

            String paymentId =
                    data.path("id").asText();

            String status =
                    data.path("status").asText();

            Integer amount =
                    data.path("amount").asInt();

            String currency =
                    data.path("currency").asText();

            String givenId =
                    data.path("metadata")
                            .path("givenId")
                            .asText();

            PaymentIntent intent =
                    paymentIntentRepository
                            .findByGivenId(givenId)
                            .orElseThrow(() ->
                                    new ApiException("PaymentIntent not found"));

            if (intent.getPaymentStatus() == PaymentStatus.PAID) {
                return;
            }

            if (!"paid".equalsIgnoreCase(status)) {

                intent.setPaymentStatus(PaymentStatus.FAILED);

                paymentIntentRepository.save(intent);

                return;
            }

            if (!currency.equals("SAR")) {
                throw new ApiException("Currency mismatch");
            }

            if (!amount.equals(intent.getInitialPaymentAmountHalalah())) {
                throw new ApiException("Amount mismatch");
            }

            completePayment(intent, paymentId);
            intent.setPaymentId(paymentId);
            paymentIntentRepository.save(intent);

            log.info(
                    "Payment {} confirmed for intent {}",
                    paymentId,
                    intent.getId()
            );

        }
        catch (Exception e) {
            log.error("Webhook Error", e);
        }


    }

    private CarServiceRequest createRequestFromIntent(PaymentIntent intent) {

        CarServiceRequest req = new CarServiceRequest();

        req.setCustomer(intent.getCustomer());
        req.setCar(intent.getCar());

        req.setInitialTransactionId(intent.getPaymentId());
        req.setPaymentId(intent.getPaymentId());

        req.setServiceOption(intent.getServiceOption());
        req.setProblemDescription(intent.getProblemDescription());

        req.setAppointmentDate(intent.getAppointmentDate());
        req.setAppointmentTime(intent.getAppointmentTime());

        req.setHydraulicTruck(intent.getHydraulicTruck());

        req.setLocation(intent.getLocation());

        req.setPaymentMethod(intent.getPaymentMethod());

        req.setEstimatedPrice(intent.getEstimatedPrice());
        req.setOriginalPrice(intent.getOriginalPrice());
        req.setDiscount(intent.getDiscount());
        req.setVatAmount(intent.getVatAmount());

        req.setCouponValid(intent.getCouponValid());
        req.setPricingMessage(intent.getPricingMessage());

        req.setInitialPaid(true);

        req.setInitialPaymentMethod(intent.getPaymentMethod());

        req.setInitialPaymentStatus(PaymentStatus.PAID);

        req.setInitialPaymentAmount(intent.getEstimatedPrice());

        req.setInitialPaymentAmountHalalah(
                intent.getInitialPaymentAmountHalalah()
        );

        req.setRemainingAmount(0.0);

        req.setCustomerStatus(CustomerRequestStatus.REQUEST_CREATED);

        req.setStaffStatus(StaffRequestStatus.NEW);

        req.setStage(WorkflowStage.NEW_REQUEST);

        req.setCreatedAt(LocalDateTime.now());

        req.setLastUpdated(LocalDateTime.now());

        CarServiceRequest saved = requestRepository.save(req);

        saved.setOrderNumber(
                String.format("ORD-%06d", saved.getId())
        );
        notificationService.send(

                saved.getCustomer().getUser(),

                NotificationType.REQUEST_CREATED,
                NotificationCategory.REQUEST,

                "تم إنشاء طلبك",

                "تم إنشاء طلبك #"
                        + saved.getOrderNumber()
                        + " بنجاح.",

                NotificationActionType.OPEN_ENTITY,

                NotificationEntityType.REQUEST,

                saved.getId().toString(),

                NotificationSection.REQUESTS
        );

        socketService.send(
                "/topic/current-orders/" +
                        req.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(req)
        );

        socketService.send(
                "/topic/request/" + req.getId(),
                carServiceRequestService.toDetailsDto(req)
        );

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );
        return requestRepository.save(saved);



    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


    //انشاء الدفعه الثانيه من الدفع بالتطبيق
    @Transactional
    public FinalMobilePaymentResponse prepareFinalMobilePayment(
            Integer requestId,
            HttpServletRequest httpRequest
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        // 1. جلب الطلب
        CarServiceRequest request =
                requestRepository.findByIdForUpdate(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        // 2. التحقق من ملكية الطلب قبل أي عملية دفع
        if (request.getCustomer() == null
                || user.getCustomer() == null
                || !request.getCustomer().getId()
                .equals(user.getCustomer().getId())) {

            throw new ApiException("غير مصرح لك");
        }

        // 3. التأكد أن الدفعة النهائية لم تتم مسبقًا
        if (Boolean.TRUE.equals(request.isFinalPaid())) {
            throw new ApiException("تم سداد الدفعة النهائية مسبقاً");
        }

        // 4. التأكد من وجود مبلغ صحيح
        if (request.getFinalPrice() == null
                || request.getFinalPrice() <= 0) {

            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        // 5. التحقق من حالة الموافقة
        RequestApproval approval =
                approvalRepo.findByRequest_Id(requestId)
                        .orElseThrow(() ->
                                new ApiException("لا يوجد تقرير للموافقة"));

        /*
         * حسب منطق النظام عندك:
         *
         * approved = false
         * → لم تتم الدفعة النهائية بعد
         *
         * approved = true
         * → الدفعة النهائية تمت وتم اعتماد التقرير
         *
         * لذلك لا تسمحي بإنشاء دفعة جديدة إذا كانت true.
         */
        if (Boolean.TRUE.equals(approval.getApproved())) {
            throw new ApiException("تمت الموافقة على التقرير مسبقاً");
        }

        // 6. البحث عن PaymentIntent موجود لنفس الطلب ونفس نوع الدفعة
        PaymentIntent existingIntent =
                paymentIntentRepository.findByServiceRequestIdAndType(
                        request.getId(),
                        PaymentIntentType.FINAL_PAYMENT
                ).orElse(null);

        if (existingIntent != null) {

            PaymentStatus status = existingIntent.getPaymentStatus();

            // 6.1 إذا تمت الدفعة بالفعل
            if (status == PaymentStatus.PAID) {
                throw new ApiException("تم سداد الدفعة النهائية مسبقاً");
            }

            // 6.2 إذا كانت محاولة الدفع ما زالت صالحة
            boolean notExpired =
                    existingIntent.getExpiresAt() != null
                            && existingIntent.getExpiresAt()
                            .isAfter(LocalDateTime.now());

            boolean paymentStillActive =
                    status == PaymentStatus.INITIATED
                            || status == PaymentStatus.PENDING;

            if (notExpired && paymentStillActive) {

                return new FinalMobilePaymentResponse(
                        existingIntent.getId(),
                        existingIntent.getGivenId(),
                        existingIntent.getInitialPaymentAmountHalalah(),
                        "SAR",
                        "PREPARED"
                );
            }

            /*
             * 6.3 يوجد Intent قديم لكنه منتهي
             *
             * بدل إنشاء Row جديد ثم الاصطدام بـ Unique Constraint
             * نعيد استخدام نفس الـPaymentIntent ونحدّث بيانات المحاولة.
             */
            existingIntent.setCustomer(user.getCustomer());
            existingIntent.setPaymentMethod(request.getPaymentMethod());

            existingIntent.setInitialPaymentAmount(
                    request.getFinalPrice().doubleValue()
            );

            int amount = request.getFinalPrice() * 100;

            existingIntent.setInitialPaymentAmountHalalah(amount);

            existingIntent.setPaymentStatus(PaymentStatus.INITIATED);

            existingIntent.setCreatedAt(LocalDateTime.now());
            existingIntent.setExpiresAt(
                    LocalDateTime.now().plusMinutes(5)
            );

            existingIntent.setGivenId(UUID.randomUUID().toString());

            existingIntent.setPaymentId(null);
            existingIntent.setPaidAt(null);

            existingIntent.setType(PaymentIntentType.FINAL_PAYMENT);

            paymentIntentRepository.save(existingIntent);

            return new FinalMobilePaymentResponse(
                    existingIntent.getId(),
                    existingIntent.getGivenId(),
                    amount,
                    "SAR",
                    "PREPARED"
            );
        }

        // 7. لا يوجد PaymentIntent سابق
        // ننشئ واحدًا جديدًا
        PaymentIntent intent = new PaymentIntent();

        intent.setCustomer(user.getCustomer());
        intent.setServiceRequest(request);
        intent.setPaymentMethod(request.getPaymentMethod());

        intent.setInitialPaymentAmount(
                request.getFinalPrice().doubleValue()
        );

        int amount = request.getFinalPrice() * 100;

        intent.setInitialPaymentAmountHalalah(amount);

        intent.setPaymentStatus(PaymentStatus.INITIATED);

        intent.setCreatedAt(LocalDateTime.now());
        intent.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );

        intent.setGivenId(UUID.randomUUID().toString());

        intent.setType(PaymentIntentType.FINAL_PAYMENT);

        paymentIntentRepository.save(intent);

        return new FinalMobilePaymentResponse(
                intent.getId(),
                intent.getGivenId(),
                amount,
                "SAR",
                "PREPARED"
        );
    }

    // الرد على الدفعه الثانيه من التطبيق
    @Transactional
    public ConfirmFinalMobilePaymentResponse confirmFinalMobilePayment(
            ConfirmMobilePaymentRequest dto
    ) {

        PaymentIntent intent =
                paymentIntentRepository.findById(dto.getPaymentAttemptId())
                        .orElseThrow(() ->
                                new ApiException("Payment attempt not found"));

        if (intent.getServiceRequest() == null) {
            throw new ApiException("Not a final payment");
        }

        if (intent.getPaymentStatus() == PaymentStatus.PAID) {

            CarServiceRequest request = intent.getServiceRequest();

            return new ConfirmFinalMobilePaymentResponse(
                    request.getId(),
                    intent.getId(),
                    intent.getPaymentId(),
                    PaymentStatus.PAID.name(),
                    request.getCustomerStatus().name()
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "https://api.moyasar.com/v1/payments/" + dto.getPaymentId(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

        Map<String, Object> payment = response.getBody();

        if (payment == null) {
            throw new ApiException("Payment not found");
        }

        if (!"paid".equalsIgnoreCase(payment.get("status").toString())) {
            throw new ApiException(
                    "لم يتم إكمال عملية الدفع، يرجى المحاولة مرة أخرى."
            );
        }

        Integer amount =
                ((Number) payment.get("amount")).intValue();

        if (!amount.equals(intent.getInitialPaymentAmountHalalah())) {
            throw new ApiException("Amount mismatch");
        }

        if (!"SAR".equals(payment.get("currency").toString())) {
            throw new ApiException("Currency mismatch");
        }

        Object metaObj = payment.get("metadata");

        if (!(metaObj instanceof Map<?, ?> metadata)) {
            throw new ApiException("Metadata missing");
        }

        if (!intent.getGivenId().equals(metadata.get("givenId"))) {
            throw new ApiException("GivenId mismatch");
        }

        RequestResponseDto request =
                completePayment(intent, dto.getPaymentId());

        return new ConfirmFinalMobilePaymentResponse(

                request.getId(),

                intent.getId(),

                dto.getPaymentId(),

                PaymentStatus.PAID.name(),

                request.getStatus()
        );
    }

// helpar
    private String formatEnglishPlate(String plate) {

        if (plate == null || plate.length() < 4) {
            return plate;
        }

        String letters = plate.substring(0, 3);
        String numbers = plate.substring(3);

        return letters + "-" + numbers;
    }

    private String formatArabicPlate(String plate) {

        if (plate == null || plate.isBlank()) {
            return plate;
        }

        String[] parts = plate.trim().split("\\s+");

        if (parts.length == 4) {
            return parts[0] + " " + parts[1] + " " + parts[2] + " - " + parts[3];
        }

        return plate;
    }

    private String resolveInvoicePaymentId(String invoiceId) {

        HttpHeaders headers = new HttpHeaders();

        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "https://api.moyasar.com/v1/invoices/" + invoiceId,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

        Map data = response.getBody();

        if (data == null) {
            throw new ApiException(
                    "تعذر الحصول على بيانات الفاتورة"
            );
        }

        Object paymentsObject =
                data.get("payments");

        if (!(paymentsObject instanceof java.util.List<?> payments)
                || payments.isEmpty()) {

            throw new ApiException(
                    "لا توجد عملية دفع مرتبطة بالفاتورة"
            );
        }

        for (Object item : payments) {

            if (!(item instanceof Map<?, ?> payment)) {
                continue;
            }

            Object status =
                    payment.get("status");

            Object id =
                    payment.get("id");

            if (id != null
                    && status != null
                    && "paid".equalsIgnoreCase(
                    status.toString()
            )) {

                return id.toString();
            }
        }

        throw new ApiException(
                "لم يتم العثور على عملية دفع مكتملة"
        );
    }

    private Integer calculateRefundAmount(
            CarServiceRequest request
    ) {

        if (!request.isInitialPaid()) {
            throw new ApiException(
                    "لا توجد دفعة أولى مستحقة للاسترداد"
            );
        }

        if (request.getInitialPaymentAmountHalalah() == null
                || request.getInitialPaymentAmountHalalah() <= 0) {

            throw new ApiException(
                    "مبلغ الدفعة الأولى غير صحيح"
            );
        }

        // مؤقتًا: Refund 100%
        return request.getInitialPaymentAmountHalalah();
    }
    private String refundMoyasarPayment(
            String paymentId,
            Integer amountHalalah
    ) {

        if (paymentId == null || paymentId.isBlank()) {
            throw new ApiException(
                    "رقم عملية الدفع غير موجود"
            );
        }

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBasicAuth(
                secretKey,
                ""
        );

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "amount",
                amountHalalah
        );

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "https://api.moyasar.com/v1/payments/"
                                + paymentId
                                + "/refund",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                body,
                                headers
                        ),
                        Map.class
                );

        Map data =
                response.getBody();

        if (data == null) {
            throw new ApiException(
                    "لم يتم تنفيذ الاسترداد"
            );
        }

        String status =
                data.get("status") != null
                        ? data.get("status").toString()
                        : null;

        if (!"refunded".equalsIgnoreCase(status)) {

            throw new ApiException(
                    "فشل استرداد المبلغ من Moyasar"
            );
        }

        return data.get("id") != null
                ? data.get("id").toString()
                : paymentId;
    }

    @Transactional
    public void refundInitialPayment(
            CarServiceRequest request,
            RefundMethod refundMethod
    ) {

        if (!request.isInitialPaid()) {

            throw new ApiException(
                    "لا توجد دفعة أولى لاستردادها"
            );
        }

        if (request.isFinalPaid()) {

            throw new ApiException(
                    "لا يمكن استرداد الدفعة بعد سداد الدفعة النهائية"
            );
        }

        if (request.getRefundStatus()
                == RefundStatus.REFUNDED) {

            throw new ApiException(
                    "تم استرداد مبلغ الطلب مسبقًا"
            );
        }

        if (refundMethod == null) {

            throw new ApiException(
                    "يرجى اختيار طريقة استرداد المبلغ"
            );
        }

        Integer refundAmount =
                calculateRefundAmount(request);

        User user =
                request.getCustomer().getUser();

        String reference =
                "REFUND-" + request.getOrderNumber();

        request.setRefundStatus(
                RefundStatus.PROCESSING
        );

        request.setRefundMethod(
                refundMethod
        );

        request.setRefundAmountHalalah(
                refundAmount
        );

        requestRepository.save(request);

        try {

            String refundTransactionId;

            if (refundMethod == RefundMethod.WALLET) {

                walletService.refundToWallet(
                        user,
                        refundAmount,
                        reference,
                        "استرداد مبلغ إلغاء الطلب #"
                                + request.getOrderNumber()
                );

                refundTransactionId =
                        reference;

            } else {

                refundTransactionId =
                        getOriginalPaymentId(request);

                refundTransactionId =
                        refundMoyasarPayment(
                                refundTransactionId,
                                refundAmount
                        );
            }

            request.setRefundTransactionId(
                    refundTransactionId
            );

            request.setRefundStatus(
                    RefundStatus.REFUNDED
            );

            request.setRefundedAt(
                    LocalDateTime.now()
            );

            request.setInitialPaymentStatus(
                    PaymentStatus.REFUNDED
            );

            request.setRefunded(true);
            requestRepository.save(request);

        } catch (Exception e) {

            request.setRefundStatus(
                    RefundStatus.FAILED
            );

            requestRepository.save(request);

            throw e;
        }
    }

    private String getOriginalPaymentId(
            CarServiceRequest request
    ) {

        if (request.getInitialTransactionId() != null
                && !request.getInitialTransactionId().isBlank()) {

            return request.getInitialTransactionId();
        }

        PaymentIntent intent =
                paymentIntentRepository
                        .findByServiceRequestIdAndType(
                                request.getId(),
                                PaymentIntentType.REQUEST
                        )
                        .orElse(null);

        if (intent != null
                && intent.getPaymentId() != null
                && !intent.getPaymentId().isBlank()) {

            return intent.getPaymentId();
        }

        if (intent != null
                && intent.getInvoiceId() != null
                && !intent.getInvoiceId().isBlank()) {

            return resolveInvoicePaymentId(
                    intent.getInvoiceId()
            );
        }

        throw new ApiException(
                "تعذر العثور على عملية الدفع الأصلية"
        );
    }

}