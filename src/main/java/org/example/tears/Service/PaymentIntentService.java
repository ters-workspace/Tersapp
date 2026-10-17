
package org.example.tears.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // إنشاء فاتورة دفع خارجية لإنشاء الطلب
    @Transactional
    public CheckoutResponse createCheckout(
            HttpServletRequest request,
            CreateRequestStepDto dto
    ) {
        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest draft =
                carServiceRequestService.buildValidatedRequest(user, dto);

        PaymentIntent intent = new PaymentIntent();

        // تحديد نوع محاولة الدفع لإنشاء طلب جديد
        intent.setType(PaymentIntentType.REQUEST);

        intent.setCustomer(user.getCustomer());
        intent.setCar(draft.getCar());
        intent.setServiceOption(draft.getServiceOption());
        intent.setProblemDescription(draft.getProblemDescription());
        intent.setAppointmentDate(draft.getAppointmentDate());
        intent.setAppointmentTime(draft.getAppointmentTime());
        intent.setHydraulicTruck(draft.isHydraulicTruck());
        intent.setLocation(draft.getLocation());
        intent.setPaymentMethod(draft.getPaymentMethod());

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

    // الدفع عن طريق المحفظة لإنشاء الطلب
    @Transactional
    public RequestResponseDto payRequestWithWallet(
            HttpServletRequest request,
            CreateRequestStepDto dto
    ) {
        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest req =
                carServiceRequestService.buildValidatedRequest(user, dto);

        if (req.getEstimatedPrice() == null
                || req.getEstimatedPrice() <= 0) {
            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        int amountHalalah = (int) Math.round(
                req.getEstimatedPrice() * 100
        );

        walletService.payFromWallet(
                user,
                amountHalalah,
                "REQ-" + UUID.randomUUID().toString().substring(0, 8)
        );

        req.setPaymentMethod(PaymentMethod.WALLET);
        req.setInitialPaid(true);
        req.setInitialPaymentMethod(PaymentMethod.WALLET);
        req.setInitialPaymentStatus(PaymentStatus.PAID);
        req.setInitialPaymentAmount(req.getEstimatedPrice());
        req.setInitialPaymentAmountHalalah(amountHalalah);
        req.setRemainingAmount(0.0);

        User customerUser = req.getCustomer().getUser();

        if (customerUser.getTestTechnician() != null) {
            req.setAssignedTechnician(customerUser.getTestTechnician());
        }

        req.setCustomerStatus(CustomerRequestStatus.REQUEST_CREATED);
        req.setStaffStatus(StaffRequestStatus.NEW);
        req.setStage(WorkflowStage.NEW_REQUEST);
        req.setLastUpdated(LocalDateTime.now());
        req.setCreatedAt(LocalDateTime.now());

        CarServiceRequest savedRequest = requestRepository.save(req);

        savedRequest.setOrderNumber(
                String.format("ORD-%06d", savedRequest.getId())
        );

        savedRequest = requestRepository.save(savedRequest);

        // إشعار جميع الأدمن النشطين بإنشاء الطلب
        notificationService.notifyActiveAdminsAboutNewRequest(savedRequest);

        // تحديث طلبات العميل
        socketService.send(
                "/topic/current-orders/"
                        + savedRequest.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(savedRequest)
        );

        // تحديث المواعيد المتاحة
        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        // إشعار العميل
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

    // الدفع النهائي عن طريق المحفظة
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

    // الرد على فاتورة الدفع الخارجي لإنشاء الطلب
    @Transactional
    public RequestResponseDto handleInvoiceCallback(
            Map<String, Object> payload
    ) {
        if (payload == null
                || payload.get("id") == null
                || payload.get("status") == null) {
            throw new ApiException("Invalid invoice callback payload");
        }

        String invoiceId = payload.get("id").toString();
        String status = payload.get("status").toString();

        PaymentIntent intent = paymentIntentRepository
                .findByInvoiceId(invoiceId)
                .orElseThrow(() ->
                        new ApiException("Payment intent not found"));

        if (intent.getType() == PaymentIntentType.FINAL_PAYMENT) {
            throw new ApiException("This invoice is for final payment");
        }

        if (!"paid".equalsIgnoreCase(status)) {
            intent.setPaymentStatus(PaymentStatus.FAILED);
            paymentIntentRepository.save(intent);
            throw new ApiException("Payment failed");
        }

        // منع إنشاء الطلب مرتين عند تكرار callback
        if (intent.getServiceRequest() != null) {
            return carServiceRequestService.toResponseDto(
                    intent.getServiceRequest()
            );
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
            req.setAssignedTechnician(customerUser.getTestTechnician());
        }

        req.setCustomerStatus(CustomerRequestStatus.REQUEST_CREATED);
        req.setStaffStatus(StaffRequestStatus.NEW);
        req.setStage(WorkflowStage.NEW_REQUEST);
        req.setLastUpdated(LocalDateTime.now());
        req.setCreatedAt(LocalDateTime.now());

        CarServiceRequest savedRequest = requestRepository.save(req);

        savedRequest.setOrderNumber(
                String.format("ORD-%06d", savedRequest.getId())
        );

        String paymentId = resolveInvoicePaymentId(invoiceId);

        savedRequest.setInitialTransactionId(paymentId);
        savedRequest.setPaymentId(paymentId);

        savedRequest = requestRepository.save(savedRequest);

        intent.setServiceRequest(savedRequest);
        intent.setPaymentStatus(PaymentStatus.PAID);
        intent.setPaymentId(paymentId);
        intent.setPaidAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

        // إشعار جميع الأدمن النشطين بإنشاء الطلب
        notificationService.notifyActiveAdminsAboutNewRequest(savedRequest);

        socketService.send(
                "/topic/current-orders/"
                        + savedRequest.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(savedRequest)
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
                .orElseThrow(() ->
                        new ApiException("Payment intent not found"));

        if (intent.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ApiException("Payment is not completed yet");
        }

        if (intent.getServiceRequest() == null) {
            throw new ApiException("Request was not created yet");
        }

        return carServiceRequestService.toResponseDto(intent.getServiceRequest());
    }

    // إنشاء فاتورة الدفع للتقرير
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

        // هذه المحاولة خاصة بالدفعة النهائية
        intent.setType(PaymentIntentType.FINAL_PAYMENT);
        intent.setCustomer(user.getCustomer());
        intent.setServiceRequest(request);
        intent.setPaymentMethod(request.getPaymentMethod());
        intent.setInitialPaymentAmount(request.getFinalPrice().doubleValue());

        int amountHalalah = request.getFinalPrice() * 100;
        intent.setInitialPaymentAmountHalalah(amountHalalah);

        // لا يتم اعتماد التقرير أو اعتبار الطلب مدفوعًا هنا.
        // تأكيد الدفع وتحديث حالة الطلب يتم في callback بعد نجاح الدفع.
        intent.setPaymentStatus(PaymentStatus.INITIATED);
        intent.setCreatedAt(LocalDateTime.now());
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        paymentIntentRepository.save(intent);

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

        ResponseEntity<Map> response = restTemplate.postForEntity(
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
        if (payload == null
                || payload.get("id") == null
                || payload.get("status") == null) {
            throw new ApiException("Invalid invoice callback payload");
        }

        String invoiceId = payload.get("id").toString();
        String status = payload.get("status").toString();

        PaymentIntent intent = paymentIntentRepository
                .findByInvoiceId(invoiceId)
                .orElseThrow(() ->
                        new ApiException("Payment intent not found"));

        if (intent.getType() != PaymentIntentType.FINAL_PAYMENT) {
            throw new ApiException("This invoice is not a final payment");
        }

        if (!"paid".equalsIgnoreCase(status)) {
            intent.setPaymentStatus(PaymentStatus.FAILED);
            paymentIntentRepository.save(intent);
            throw new ApiException("Payment failed");
        }

        CarServiceRequest request = intent.getServiceRequest();

        if (request == null) {
            throw new ApiException("Request not found");
        }

        // منع تكرار تنفيذ المعالجة عند تكرار callback
        if (intent.getPaymentStatus() == PaymentStatus.PAID) {
            return carServiceRequestService.toResponseDto(request);
        }

        String paymentId = resolveInvoicePaymentId(invoiceId);

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

        intent.setPaymentStatus(PaymentStatus.PAID);
        intent.setPaymentId(paymentId);
        intent.setPaidAt(LocalDateTime.now());

        paymentIntentRepository.save(intent);

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

    // إكمال الدفع سواء كان لإنشاء طلب أو للدفعة النهائية
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
                    && intent.getPaymentId().equals(paymentId)
                    && intent.getServiceRequest() != null) {
                return carServiceRequestService.toResponseDto(
                        intent.getServiceRequest()
                );
            }

            throw new ApiException("Payment is not valid for completion");
        }

        CarServiceRequest request = intent.getServiceRequest();

        // الدفع لإنشاء طلب جديد
        if (intent.getType() == PaymentIntentType.REQUEST
                && request == null) {

            request = createRequestFromIntent(intent);

            request.setInitialTransactionId(paymentId);
            request.setPaymentId(paymentId);

            requestRepository.save(request);
            intent.setServiceRequest(request);

        } else {
            // الدفع النهائي لطلب موجود
            if (request == null
                    || intent.getType() != PaymentIntentType.FINAL_PAYMENT) {
                throw new ApiException("Payment intent type or request is invalid");
            }

            RequestApproval approval =
                    approvalRepo.findByRequest_Id(request.getId())
                            .orElseThrow(() ->
                                    new ApiException("Approval not found"));

            if (request.isFinalPaid()
                    || Boolean.TRUE.equals(approval.getApproved())) {
                throw new ApiException("Final payment already completed");
            }

            approval.setApproved(true);
            approval.setDecisionAt(LocalDateTime.now());
            approvalRepo.save(approval);

            request.setFinalPaid(true);
            request.setFinalTransactionId(paymentId);
            request.setNextPaymentMethod(intent.getPaymentMethod());
            request.setNextPaymentStatus(PaymentStatus.PAID);
            request.setCustomerStatus(CustomerRequestStatus.UNDER_REPAIR);
            request.setStaffStatus(StaffRequestStatus.REPAIRING);
            request.setStage(WorkflowStage.REPAIRING);

            User customerUser = request.getCustomer().getUser();

            if (customerUser.getTestTechnician() != null) {
                request.setAssignedTechnician(
                        customerUser.getTestTechnician()
                );
            }

            request.setRepairAt(LocalDateTime.now());
            request.setLastUpdated(LocalDateTime.now());

            requestRepository.save(request);

            // تسجيل استخدام الكوبون إن وجد
            if (intent.getCouponCode() != null
                    && !intent.getCouponCode().isBlank()) {

                Customer customer = request.getCustomer();
                ServiceOption option = request.getServiceOption();

                Coupon coupon = couponService.validate(
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
                    "/topic/current-orders/"
                            + request.getCustomer().getUser().getId(),
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

    // إنشاء محاولة دفع داخل التطبيق لطلب جديد
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
                (int) Math.round(draft.getEstimatedPrice() * 100)
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

    // تأكيد الدفع داخل التطبيق
    @Transactional
    public ConfirmMobilePaymentResponse confirmMobilePayment(
            ConfirmMobilePaymentRequest dto
    ) {
        PaymentIntent intent = paymentIntentRepository
                .findById(dto.getPaymentAttemptId())
                .orElseThrow(() ->
                        new ApiException("Payment attempt not found"));

        if (intent.getType() != PaymentIntentType.REQUEST) {
            throw new ApiException("Not a request payment");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.moyasar.com/v1/payments/" + dto.getPaymentId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Map<String, Object> payment = response.getBody();
        log.info("MOYASAR PAYMENT RESPONSE: {}", payment);

        if (payment == null) {
            throw new ApiException("Payment not found");
        }

        String status = payment.get("status").toString();

        if (!"paid".equalsIgnoreCase(status)) {
            throw new ApiException("Payment not completed");
        }

        Integer amount = ((Number) payment.get("amount")).intValue();

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

        if (metadata.get("givenId") == null
                || !intent.getGivenId().equals(metadata.get("givenId").toString())) {
            throw new ApiException("GivenId mismatch");
        }

        RequestResponseDto request =
                completePayment(intent, dto.getPaymentId());

        CarServiceRequest serviceRequest = requestRepository
                .findById(request.getId())
                .orElseThrow(() ->
                        new ApiException("Request not found"));

        String carInfo =
                formatArabicPlate(serviceRequest.getCar().getPlateNumberArabic())
                        + "\n"
                        + formatEnglishPlate(
                        serviceRequest.getCar().getPlateNumberEnglish()
                );

        String appointmentDate = serviceRequest.getAppointmentDate()
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
                formatArabicPlate(
                        serviceRequest.getCar().getPlateNumberArabic()
                ),
                formatEnglishPlate(
                        serviceRequest.getCar().getPlateNumberEnglish()
                ),
                serviceRequest.getEstimatedPrice(),
                intent.getId(),
                dto.getPaymentId(),
                PaymentStatus.PAID.name()
        );
    }

    // Webhook
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

            String paymentId = data.path("id").asText();
            String status = data.path("status").asText();
            Integer amount = data.path("amount").asInt();
            String currency = data.path("currency").asText();
            String givenId = data.path("metadata").path("givenId").asText();

            PaymentIntent intent = paymentIntentRepository
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

            if (!"SAR".equals(currency)) {
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

        } catch (Exception e) {
            log.error("Webhook Error", e);
        }
    }

    // إنشاء طلب من PaymentIntent بعد نجاح الدفع داخل التطبيق
    private CarServiceRequest createRequestFromIntent(
            PaymentIntent intent
    ) {
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

        User customerUser = req.getCustomer().getUser();

        if (customerUser.getTestTechnician() != null) {
            req.setAssignedTechnician(customerUser.getTestTechnician());
        }

        CarServiceRequest saved = requestRepository.save(req);

        saved.setOrderNumber(
                String.format("ORD-%06d", saved.getId())
        );

        saved = requestRepository.save(saved);

        // إشعار الأدمن بالطلب الجديد
        notificationService.notifyActiveAdminsAboutNewRequest(saved);

        // إشعار العميل
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
                "/topic/current-orders/"
                        + saved.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(saved)
        );

        socketService.send(
                "/topic/request/" + saved.getId(),
                carServiceRequestService.toDetailsDto(saved)
        );

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        return saved;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // إنشاء الدفعة الثانية من الدفع بالتطبيق
    @Transactional
    public FinalMobilePaymentResponse prepareFinalMobilePayment(
            Integer requestId,
            HttpServletRequest httpRequest
    ) {
        User user = authService.getAuthenticatedUser(httpRequest);

        CarServiceRequest request = requestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() ->
                        new ApiException("الطلب غير موجود"));

        if (request.getCustomer() == null
                || user.getCustomer() == null
                || !request.getCustomer().getId()
                .equals(user.getCustomer().getId())) {
            throw new ApiException("غير مصرح لك");
        }

        if (Boolean.TRUE.equals(request.isFinalPaid())) {
            throw new ApiException("تم سداد الدفعة النهائية مسبقاً");
        }

        if (request.getFinalPrice() == null
                || request.getFinalPrice() <= 0) {
            throw new ApiException("لا يوجد مبلغ للدفع");
        }

        RequestApproval approval = approvalRepo
                .findByRequest_Id(requestId)
                .orElseThrow(() ->
                        new ApiException("لا يوجد تقرير للموافقة"));

        if (Boolean.TRUE.equals(approval.getApproved())) {
            throw new ApiException("تمت الموافقة على التقرير مسبقاً");
        }

        PaymentIntent existingIntent = paymentIntentRepository
                .findByServiceRequestIdAndType(
                        request.getId(),
                        PaymentIntentType.FINAL_PAYMENT
                ).orElse(null);

        if (existingIntent != null) {
            PaymentStatus status = existingIntent.getPaymentStatus();

            if (status == PaymentStatus.PAID) {
                throw new ApiException("تم سداد الدفعة النهائية مسبقاً");
            }

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

            existingIntent.setCustomer(user.getCustomer());
            existingIntent.setServiceRequest(request);
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
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));
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

    // تأكيد الدفعة الثانية من التطبيق
    @Transactional
    public ConfirmFinalMobilePaymentResponse confirmFinalMobilePayment(
            ConfirmMobilePaymentRequest dto
    ) {
        PaymentIntent intent = paymentIntentRepository
                .findById(dto.getPaymentAttemptId())
                .orElseThrow(() ->
                        new ApiException("Payment attempt not found"));

        if (intent.getType() != PaymentIntentType.FINAL_PAYMENT
                || intent.getServiceRequest() == null) {
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

        ResponseEntity<Map> response = restTemplate.exchange(
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

        Integer amount = ((Number) payment.get("amount")).intValue();

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

        if (metadata.get("givenId") == null
                || !intent.getGivenId().equals(metadata.get("givenId").toString())) {
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

    // تنسيق لوحة السيارة الإنجليزية
    private String formatEnglishPlate(String plate) {
        if (plate == null || plate.length() < 4) {
            return plate;
        }

        String letters = plate.substring(0, 3);
        String numbers = plate.substring(3);

        return letters + "-" + numbers;
    }

    // تنسيق لوحة السيارة العربية
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

    // استخراج معرف عملية الدفع المكتملة من فاتورة Moyasar
    private String resolveInvoicePaymentId(String invoiceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "");

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.moyasar.com/v1/invoices/" + invoiceId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Map data = response.getBody();

        if (data == null) {
            throw new ApiException("تعذر الحصول على بيانات الفاتورة");
        }

        Object paymentsObject = data.get("payments");

        if (!(paymentsObject instanceof java.util.List<?> payments)
                || payments.isEmpty()) {
            throw new ApiException("لا توجد عملية دفع مرتبطة بالفاتورة");
        }

        for (Object item : payments) {
            if (!(item instanceof Map<?, ?> payment)) {
                continue;
            }

            Object status = payment.get("status");
            Object id = payment.get("id");

            if (id != null
                    && status != null
                    && "paid".equalsIgnoreCase(status.toString())) {
                return id.toString();
            }
        }

        throw new ApiException("لم يتم العثور على عملية دفع مكتملة");
    }
}
