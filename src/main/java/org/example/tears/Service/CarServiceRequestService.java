package org.example.tears.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.InpDTO.LocationDto;
import org.example.tears.InpDTO.PreviewRequestDto;
import org.example.tears.InpDTO.CreateRequestStepDto;
import org.example.tears.InpDTO.UpdateRequestDto;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.OutDTO.PreviewResponseDto;
import org.example.tears.OutDTO.PricingResponse;
import org.example.tears.OutDTO.RequestResponseDto;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarServiceRequestService {

    private final CarServiceRequestRepository requestRepository;
    private final CarRepository carRepository;
    private final AuthService authService;
    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final AppointmentService appointmentService;
    private final RequestImageRepository imageRepo;
    private final RequestReportRepository reportRepo;
    private final PricingCalculationService pricingCalculationService;
    private final RequestReviewRepository reviewRepository;
    private final WarrantyRepository warrantyRequestRepository;
    private final SocketService socketService;
    private final RequestMapper requestMapper;
    private final NotificationService notificationService;
    private final PaymentIntentService paymentIntentService;


    // ---------------------------
    // Step 1: Preview
    // ---------------------------
    public PreviewResponseDto preview(PreviewRequestDto dto) {

        ServiceOption option =
                ServiceOption.valueOf(dto.getServiceOption());

        double servicePrice = option.getPrice() * 1.15;

        double hydraulicPrice =
                option == ServiceOption.ELECTRONIC_CHECK
                        ? 0
                        : 150 * 1.15;

        PreviewResponseDto resp = new PreviewResponseDto();

        resp.setServicePrice(servicePrice);

        resp.setHydraulicTruckPrice(hydraulicPrice);

        return resp;
    }


    // ---------------------------
    // Step 2: Create Final Request
    // ---------------------------
    @Transactional
    public RequestResponseDto createRequest(HttpServletRequest request, CreateRequestStepDto dto) {
        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest req = buildValidatedRequest(user, dto);

        CarServiceRequest saved = requestRepository.save(req);

        return toResponseDto(saved);
    }

    public RequestResponseDto updateRequest(
            HttpServletRequest request,
            Integer requestId,
            UpdateRequestDto dto
    ) {
        User user = authService.getAuthenticatedUser(request);

        CarServiceRequest serviceRequest = requestRepository
                .findById(requestId)
                .orElseThrow(() ->
                        new ApiException("الطلب غير موجود")
                );
        // =========================
        // Car
        // =========================

        Car car = carRepository.findById(dto.getCarId())
                .orElseThrow(() -> new ApiException("السيارة غير موجودة"));

        serviceRequest.setCar(car);

// ownership check
        if (!serviceRequest.getCustomer().getId()
                .equals(user.getCustomer().getId())) {

            throw new ApiException("هذا الطلب لا يخص المستخدم");
        }

// prevent edit after payment
        if (serviceRequest.isInitialPaid()) {
            throw new ApiException("لا يمكن تعديل الطلب بعد الدفع");
        }

// =========================
// Problem Description
// =========================
        if (dto.getProblemDescription() != null) {

            serviceRequest.setProblemDescription(
                    dto.getProblemDescription()
            );
        }

// =========================
// Hydraulic Truck
// =========================
        if (dto.getHydraulicTruck() != null) {

            serviceRequest.setHydraulicTruck(
                    dto.getHydraulicTruck()
            );
        }

// =========================
// Appointment
// =========================
        if (dto.getAppointmentDate() != null
                && dto.getAppointmentTime() != null) {

            appointmentService.validateAppointment(
                    dto.getAppointmentDate(),
                    dto.getAppointmentTime()
            );

            serviceRequest.setAppointmentDate(
                    dto.getAppointmentDate()
            );

            serviceRequest.setAppointmentTime(
                    dto.getAppointmentTime()
            );
        }

// =========================
// Service Option
// =========================
        if (dto.getServiceOption() != null) {

            ServiceOption option = ServiceOption.valueOf(
                    dto.getServiceOption().toUpperCase()
            );

            serviceRequest.setServiceOption(option);
        }
        if (serviceRequest.getCustomerStatus()
                == CustomerRequestStatus.CANCELED) {

            throw new ApiException("لا يمكن تعديل طلب ملغي");
        }

// =========================
// Payment Method
// =========================
        if (dto.getPaymentMethod() != null) {

            PaymentMethod method = PaymentMethod.valueOf(
                    dto.getPaymentMethod().toUpperCase()
            );

            serviceRequest.setPaymentMethod(method);
        }

// =========================
// Location
// =========================
        if (dto.getLocationId() != null) {

            Location location = locationRepository
                    .findById(dto.getLocationId())
                    .orElseThrow(() ->
                            new ApiException("الموقع غير موجود")
                    );

            serviceRequest.setLocation(location);
        }
// =========================
// Recalculate Price
// =========================
        PricingResponse pricing = pricingCalculationService.calculateFinal(
                serviceRequest.getServiceOption().name(),
                serviceRequest.isHydraulicTruck(),
                dto.getCouponCode(),
                user.getCustomer()
        );

        serviceRequest.setEstimatedPrice(pricing.finalPrice);
        serviceRequest.setOriginalPrice(pricing.originalPrice);
        serviceRequest.setDiscount(pricing.discount);
        serviceRequest.setVatAmount(pricing.vatAmount);
        serviceRequest.setCouponValid(pricing.couponValid);
        serviceRequest.setPricingMessage(pricing.message);

        serviceRequest.setLastUpdated(LocalDateTime.now());

        CarServiceRequest updated = requestRepository.save(serviceRequest);

        return toResponseDto(updated);
    }

    public List<String> getRequestImages(Integer requestId, Integer customerId) {

        CarServiceRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("غير موجود"));

        if (!req.getCustomer().getId().equals(customerId)) {
            throw new RuntimeException("غير مصرح");
        }

        return imageRepo.findByRequestIdAndVisibleToCustomerTrue(requestId)
                .stream()
                .map(RequestImage::getImageUrl)
                .toList();
    }

    public List<CurrentRequestDto> getCurrentRequests(Integer customerId) {

        List<CarServiceRequest> currentRequests =
                requestRepository
                        .findByCustomerIdAndCustomerStatusNotInOrderByCreatedAtDesc(
                                customerId,
                                List.of(
                                        CustomerRequestStatus.DELIVERED,
                                        CustomerRequestStatus.CANCELED
                                )
                        );

        List<CarServiceRequest> warrantyRequests =
                warrantyRequestRepository
                        .findByCustomer_Id(customerId)
                        .stream()
                        .filter(w ->
                                w.getCustomerStatus() != WarrantyCustomerStatus.REJECTED &&
                                        w.getCustomerStatus() != WarrantyCustomerStatus.DELIVERED
                        )
                        .map(WarrantyRequest::getRequest)
                        .filter(r ->
                                r.getCustomerStatus() == CustomerRequestStatus.DELIVERED
                        )
                        .toList();

        Map<Integer, CarServiceRequest> requestsMap =
                new LinkedHashMap<>();

        currentRequests.forEach(r ->
                requestsMap.put(r.getId(), r)
        );

        warrantyRequests.forEach(r ->
                requestsMap.put(r.getId(), r)
        );

        return requestsMap.values()
                .stream()
                .sorted(
                        Comparator.comparing(
                                CarServiceRequest::getCreatedAt,
                                Comparator.reverseOrder()
                        )
                )
                .map(this::toCurrentDto)
                .toList();
    }

    public List<RequestHistoryDto> getPastRequests(Integer customerId) {

        List<CarServiceRequest> requests =
                requestRepository
                        .findByCustomerIdAndCustomerStatusInOrderByCreatedAtDesc(
                                customerId,
                                List.of(
                                        CustomerRequestStatus.DELIVERED,
                                        CustomerRequestStatus.CANCELED
                                )
                        );

        List<WarrantyRequest> warranties =
                warrantyRequestRepository
                        .findByCustomer_Id(customerId);

        Set<Integer> activeWarrantyRequestIds =
                warranties.stream()
                        .filter(w ->
                                w.getCustomerStatus() != WarrantyCustomerStatus.REJECTED &&
                                        w.getCustomerStatus() != WarrantyCustomerStatus.DELIVERED
                        )
                        .map(w -> w.getRequest().getId())
                        .collect(Collectors.toSet());

        // أي طلب انتهى لكن عنده ضمان نشط:
        // لا يظهر في Past لأنه موجود في Current
        requests.removeIf(r ->
                activeWarrantyRequestIds.contains(r.getId())
        );

        return requests.stream()
                .map(this::toHistoryDto)
                .toList();
    }



    // ---------------------------
    // عرض طلبات المستخدم
    // ---------------------------
    public List<RequestResponseDto> getMyRequests(Integer userCustomerId) {
        return requestRepository.findByCustomerIdOrderByIdDesc(userCustomerId)
                .stream().map(this::toResponseDto).collect(Collectors.toList());
    }


    public CurrentRequestDto toCurrentDto(CarServiceRequest req) {

        CurrentRequestDto dto = new CurrentRequestDto();

        dto.setId(req.getId());
        dto.setOrderNumber(req.getOrderNumber());

        if (req.getServiceOption() != null) {
            dto.setServiceName(req.getServiceOption().name());
        }

        if (req.getCustomerStatus() != null) {
            dto.setStatus(req.getCustomerStatus().name());
        }


        dto.setRequestState(
                requestMapper.mapRequestState(req)
        );

        Optional<WarrantyRequest> warranty =
                warrantyRequestRepository.findByRequestId(req.getId());

        dto.setWarrantyRequest(warranty.isPresent());

        dto.setWarrantyId(
                warranty.map(WarrantyRequest::getId)
                        .orElse(null)
        );

        dto.setWarrantyReason(
                warranty.map(WarrantyRequest::getWarrantyReason)
                        .orElse(null)
        );

        dto.setWarrantyStatus(
                warranty.map(WarrantyRequest::getCustomerStatus)
                        .orElse(null)
        );

        dto.setWarrantyEligibility(
                warranty.map(WarrantyRequest::getWarrantyEligibility)
                        .orElse(null)
        );



        return dto;
    }

    public RequestHistoryDto toHistoryDto(CarServiceRequest req) {

        RequestHistoryDto dto = new RequestHistoryDto();

        // =========================
        // Warranty
        // =========================

        Optional<WarrantyRequest> warranty =
                warrantyRequestRepository.findByRequestId(req.getId());

        dto.setWarrantyRequest(warranty.isPresent());

        dto.setWarrantyId(
                warranty.map(WarrantyRequest::getId)
                        .orElse(null)
        );

        dto.setWarrantyReason(
                warranty.map(WarrantyRequest::getWarrantyReason)
                        .orElse(null)
        );

        dto.setWarrantyStatus(
                warranty.map(WarrantyRequest::getCustomerStatus)
                        .orElse(null)
        );

        dto.setWarrantyEligibility(
                warranty.map(WarrantyRequest::getWarrantyEligibility)
                        .orElse(null)
        );

        // =========================
        // Request Information
        // =========================

        dto.setId(req.getId());
        dto.setOrderNumber(req.getOrderNumber());

        if (req.getServiceOption() != null) {
            dto.setServiceName(req.getServiceOption().name());
        }

        dto.setAppointmentDate(req.getAppointmentDate());
        dto.setAppointmentTime(req.getAppointmentTime());

        dto.setTotalPrice(
                req.getFinalPrice() != null
                        ? req.getFinalPrice().doubleValue()
                        : req.getEstimatedPrice()
        );

        // =========================
        // Review
        // =========================

        boolean reviewed =
                reviewRepository.existsByRequestId(req.getId());

        dto.setReviewed(reviewed);

        dto.setCanReview(
                req.getCustomerStatus() == CustomerRequestStatus.DELIVERED
                        && !reviewed
        );



        // =========================
        // Request State
        // =========================

        dto.setRequestState(
                requestMapper.mapRequestState(req)
        );

        if (req.getCustomerStatus() != null) {
            dto.setCustomerStatus(
                    req.getCustomerStatus().name()
            );
        }

        // =========================
        // Warranty Information
        // =========================

        if (req.getDeliveredAt() != null) {

            LocalDate expiryDate =
                    req.getDeliveredAt()
                            .toLocalDate()
                            .plusDays(30);

            dto.setWarrantyExpiryDate(expiryDate);

            boolean eligible =
                    requestMapper.isWarrantyEligible(req);

            dto.setUnderWarranty(eligible);

            dto.setWarrantyRemainingDays(
                    eligible
                            ? ChronoUnit.DAYS.between(
                            LocalDate.now(),
                            expiryDate
                    )
                            : 0L
            );
        }




        // =========================
        // Car Information
        // =========================

        Car car = req.getCar();

        if (car != null) {

            dto.setCarId(car.getId());

            // اللوحات بعد التقسيم
            dto.setPlateNumberArabic(
                    formatArabicPlate(
                            car.getPlateNumberArabic()
                    )
            );

            dto.setPlateNumberEnglish(
                    formatEnglishPlate(
                            car.getPlateNumberEnglish()
                    )
            );

            // الماركة
            if (car.getBrand() != null) {
                dto.setBrandNameAr(
                        car.getBrand().getNameAr()
                );
            }

            // الموديل
            if (car.getModel() != null) {
                dto.setModelNameAr(
                        car.getModel().getNameAr()
                );
            }

            // السنة
            dto.setCarYear(
                    car.getCarYear()
            );

            // صورة السيارة
            if (car.getModel().getImagePath() != null &&
                    !car.getModel().getImagePath().isBlank()) {

                dto.setCarImage(
                        car.getModel().getImagePath()
                );

            } else {

                dto.setCarImage(
                        "car.png"
                );
            }
        }

        return dto;
    }

    public RequestResponseDto toResponseDto(CarServiceRequest r) {

        RequestResponseDto dto = new RequestResponseDto();

        dto.setId(r.getId());
        dto.setOrderNumber(r.getOrderNumber());


        boolean warrantyRequestExists =
                warrantyRequestRepository.existsByRequestId(r.getId());

        dto.setWarrantyRequest(warrantyRequestExists);


        dto.setStatus(
                r.getCustomerStatus() != null
                        ? r.getCustomerStatus().name()
                        : "REQUEST_CREATED"
        );

        WarrantyStatus warrantyStatus = null;

        Optional<WarrantyRequest> warranty =
                warrantyRequestRepository.findByRequestId(r.getId());

        if (warranty.isPresent()) {
            warrantyStatus = warranty.get().getStatus();
        }

        dto.setWarrantyStatus(
                warrantyStatus != null ? warrantyStatus.name() : null
        );

        dto.setTotalPrice(
                r.getEstimatedPrice() != null
                        ? r.getEstimatedPrice()
                        : 0
        );

        dto.setAppointmentDate(r.getAppointmentDate());
        dto.setAppointmentTime(r.getAppointmentTime());

        dto.setHydraulicTruck(r.isHydraulicTruck());

        dto.setPaymentMethod(
                r.getPaymentMethod() != null
                        ? r.getPaymentMethod().name()
                        : null
        );

        dto.setLocation(mapLocation(r.getLocation()));
        dto.setTotalPrice(
                r.getEstimatedPrice()
        );

        dto.setOriginalPrice(
                r.getOriginalPrice()
        );

        dto.setDiscount(
                r.getDiscount()
        );

        dto.setVatAmount(
                r.getVatAmount()
        );

        dto.setCouponValid(
                r.getCouponValid()
        );

        dto.setPricingMessage(
                r.getPricingMessage()
        );
        dto.setAmountPaid(r.getInitialPaymentAmount());

        dto.setAmountPaidHalalah(r.getInitialPaymentAmountHalalah());

        dto.setInitialPaymentMethod(
                r.getInitialPaymentMethod() != null
                        ? r.getInitialPaymentMethod().name()
                        : null
        );

        dto.setInitialPaymentStatus(
                r.getInitialPaymentStatus() != null
                        ? r.getInitialPaymentStatus().name()
                        : null
        );

        dto.setRemainingAmount(r.getRemainingAmount());

        dto.setNextPaymentMethod(
                r.getNextPaymentMethod() != null
                        ? r.getNextPaymentMethod().name()
                        : null
        );

        dto.setNextPaymentStatus(
                r.getNextPaymentStatus() != null
                        ? r.getNextPaymentStatus().name()
                        : null
        );

        // 🚗 هنا أهم جزء: نجيب السيارة
        Car car = carRepository.findById(r.getCar().getId())
                .orElse(null);

        if (car != null) {
            dto.setPlateNumberArabic(
                    formatArabicPlate(r.getCar().getPlateNumberArabic())
            );

            dto.setPlateNumberEnglish(
                    formatEnglishPlate(r.getCar().getPlateNumberEnglish())
            );
        }

        return dto;
    }

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

    public LocationDto mapLocation(Location loc) {

        if (loc == null) {
            return null;
        }

        LocationDto dto = new LocationDto();

        dto.setId(loc.getId());

        dto.setLat(loc.getLat());

        dto.setLng(loc.getLng());

        dto.setAddress(loc.getAddress());

        dto.setTitle(loc.getTitle());

        return dto;
    }

    public RequestDetailsDto getRequestDetails(
            Integer customerId,
            Integer requestId
    ) {

        CarServiceRequest req = requestRepository.findById(requestId)
                .orElseThrow(() ->
                        new ApiException("الطلب غير موجود")
                );

        if (!req.getCustomer().getId().equals(customerId)) {
            throw new ApiException("غير مصرح");
        }

        return toDetailsDto(req);
    }

    public RequestDetailsDto toDetailsDto(
            CarServiceRequest req
    ) {

        RequestDetailsDto dto =
                new RequestDetailsDto();

        dto.setId(req.getId());

        dto.setOrderNumber(
                req.getOrderNumber()
        );

        dto.setServiceName(
                req.getServiceOption() != null
                        ? req.getServiceOption().name()
                        : null
        );

        dto.setCustomerStatus(
                req.getCustomerStatus() != null
                        ? req.getCustomerStatus().name()
                        : null
        );

        dto.setRequestState(
                requestMapper.mapRequestState(req)
        );

        dto.setTotalPrice(
                req.getFinalPrice() != null
                        ? req.getFinalPrice().doubleValue()
                        : req.getEstimatedPrice()
        );

        // ==========================================
        // هل صور الموظف وصلت للعميل؟
        // ==========================================

        dto.setEmployeeImagesReceived(
                !imageRepo
                        .findByRequestIdAndVisibleToCustomerTrue(req.getId())
                        .isEmpty()
        );

        // ==========================================
        // هل تقرير الموظف وصل للعميل؟
        // ==========================================

        dto.setEmployeeReportReceived(
                reportRepo.findByRequest_IdAndSentTrue(req.getId())
                        .isPresent()
        );

        // ==========================================
        // Location
        // ==========================================

        if (req.getLocation() != null) {

            dto.setLocation(
                    mapLocation(req.getLocation())
            );
        }

        // ==========================================
        // Car
        // ==========================================

        if (req.getCar() != null) {

            dto.setPlateNumberArabic(
                    formatArabicPlate(
                            req.getCar().getPlateNumberArabic()
                    )
            );

            dto.setPlateNumberEnglish(
                    formatEnglishPlate(
                            req.getCar().getPlateNumberEnglish()
                    )
            );
        }

        return dto;
    }

    @Transactional
    public void cancelRequest(
            Integer customerId,
            Integer requestId,
            CancelRequestDto dto
    ) {

        CarServiceRequest req =
                requestRepository.findByIdForUpdate(requestId)
                        .orElseThrow(() ->
                                new ApiException(
                                        "الطلب غير موجود"
                                )
                        );

        // =========================
        // Ownership
        // =========================

        if (!req.getCustomer().getId()
                .equals(customerId)) {

            throw new ApiException(
                    "غير مصرح"
            );
        }

        // =========================
        // Already cancelled
        // =========================

        if (req.getCustomerStatus()
                == CustomerRequestStatus.CANCELED) {

            throw new ApiException(
                    "الطلب ملغي مسبقًا"
            );
        }

        // =========================
        // Initial payment
        // =========================

        if (!req.isInitialPaid()) {

            throw new ApiException(
                    "لا يمكن إلغاء طلب لم يتم دفع الدفعة الأولى"
            );
        }

        // =========================
        // Final payment
        // =========================

        if (req.isFinalPaid()) {

            throw new ApiException(
                    "لا يمكن إلغاء الطلب بعد سداد الدفعة النهائية"
            );
        }

        // =========================
        // Car already received
        // =========================

        if (
                req.getCustomerStatus()
                        == CustomerRequestStatus.CAR_RECEIVED

                        || req.getCustomerStatus()
                        == CustomerRequestStatus.CAR_INSPECTION

                        || req.getCustomerStatus()
                        == CustomerRequestStatus.WAITING_APPROVAL

                        || req.getCustomerStatus()
                        == CustomerRequestStatus.UNDER_REPAIR

                        || req.getCustomerStatus()
                        == CustomerRequestStatus.READY_FOR_DELIVERY

                        || req.getCustomerStatus()
                        == CustomerRequestStatus.DELIVERED
        ) {

            throw new ApiException(
                    "لا يمكن إلغاء الطلب بعد استلام السيارة"
            );
        }

        // =========================
        // Cancellation reason
        // =========================

        if (dto.getReason() == null) {

            throw new ApiException(
                    "يرجى اختيار سبب الإلغاء"
            );
        }

        if (
                dto.getReason()
                        == CancelReason.OTHER

                        && (
                        dto.getOtherReason() == null
                                || dto.getOtherReason().isBlank()
                )
        ) {

            throw new ApiException(
                    "يرجى كتابة سبب الإلغاء"
            );
        }

        // =========================
        // Already refunded
        // =========================

        if (req.isRefunded()) {

            throw new ApiException(
                    "تم استرداد مبلغ هذا الطلب مسبقًا"
            );
        }

        // =========================
        // Refund to wallet
        // =========================

        paymentIntentService.refundInitialPayment(
                req,
                RefundMethod.WALLET
        );

        req.setRefunded(true);

        req.setRefundedAt(
                LocalDateTime.now()
        );

        // =========================
        // Save cancellation
        // =========================

        req.setCancellationReason(
                dto.getReason().name()
        );

        req.setCancellationOtherReason(
                dto.getOtherReason()
        );

        req.setCustomerStatus(
                CustomerRequestStatus.CANCELED
        );

        req.setStage(
                WorkflowStage.CANCELLED
        );

        req.setLastUpdated(
                LocalDateTime.now()
        );

        requestRepository.save(req);

        // =========================
        // Past Orders WebSocket
        // =========================

        socketService.send(
                "/topic/past-orders/"
                        + req.getCustomer()
                        .getUser()
                        .getId(),

                toHistoryDto(req)
        );

        // =========================
        // Availability WebSocket
        // =========================

        socketService.send(
                "/topic/availability",
                appointmentService.getAllAvailability()
        );

        // =========================
        // Cancellation Notification
        // =========================

        notificationService.send(

                req.getCustomer().getUser(),

                NotificationType.REQUEST_CANCELLED,
                NotificationCategory.REQUEST,

                "تم إلغاء الطلب",

                "تم إلغاء الطلب #"
                        + req.getOrderNumber()
                        + ". تم إرجاع مبلغ الدفعة الأولى إلى محفظتك.",

                NotificationActionType.OPEN_ENTITY,

                NotificationEntityType.REQUEST,

                req.getId().toString(),

                NotificationSection.REQUESTS
        );
    }




    @Transactional
    public void addReview(
            Integer customerId,
            Integer requestId,
            RequestReviewDto dto
    ) {

        CarServiceRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود")
                        );

        if (!request.getCustomer().getId()
                .equals(customerId)) {

            throw new ApiException("غير مصرح");
        }

        if (request.getCustomerStatus()
                != CustomerRequestStatus.DELIVERED) {

            throw new ApiException(
                    "لا يمكن تقييم طلب غير منتهي"
            );
        }

        if (reviewRepository.existsByRequestId(requestId)) {

            throw new ApiException(
                    "تم تقييم الطلب مسبقاً"
            );
        }

        if (dto.getRating() < 1
                || dto.getRating() > 5) {

            throw new ApiException(
                    "التقييم يجب أن يكون من 1 إلى 5"
            );
        }

        if (dto.getRating() <= 3 &&
                (dto.getComment() == null
                        || dto.getComment().isBlank())) {

            throw new ApiException(
                    "يرجى كتابة سبب التقييم"
            );
        }

        RequestReview review =
                new RequestReview();

        review.setRequest(request);
        review.setCustomer(request.getCustomer());

        review.setRating(dto.getRating());

        review.setComment(dto.getComment());

        review.setCreatedAt(LocalDateTime.now());

        reviewRepository.save(review);
    }

    public CarServiceRequest buildValidatedRequest(
            User user,
            CreateRequestStepDto dto
    ) {
        boolean ownsCar = carRepository.findByCustomerId(user.getCustomer().getId())
                .stream()
                .anyMatch(c -> c.getId().equals(dto.getCarId()));

        if (!ownsCar) {
            throw new ApiException("السيارة لا تنتمي للمستخدم");
        }

        if (dto.getProblemDescription() == null ||
                dto.getProblemDescription().isBlank()) {
            throw new ApiException("وصف المشكلة إلزامي");
        }

        ServiceOption option =
                ServiceOption.valueOf(
                        dto.getServiceOption().toUpperCase()
                );

        Location location =
                locationService.resolveLocation(dto, user);

        appointmentService.validateAppointment(
                dto.getAppointmentDate(),
                dto.getAppointmentTime()
        );

        PricingResponse pricing =
                pricingCalculationService.calculateFinal(
                        dto.getServiceOption(),
                        dto.isHydraulicTruck(),
                        dto.getCouponCode(),
                        user.getCustomer()
                );

        PaymentMethod method =
                PaymentMethod.valueOf(
                        dto.getPaymentMethod().toUpperCase()
                );

        CarServiceRequest req =
                new CarServiceRequest();

        Car car =
                carRepository.findById(dto.getCarId())
                        .orElseThrow(() ->
                                new ApiException("السيارة غير موجودة"));

        req.setCar(car);
        req.setCustomer(user.getCustomer());
        req.setServiceOption(option);
        req.setProblemDescription(dto.getProblemDescription());
        req.setHydraulicTruck(dto.isHydraulicTruck());
        req.setAppointmentDate(dto.getAppointmentDate());
        req.setAppointmentTime(dto.getAppointmentTime());

        req.setEstimatedPrice(
                pricing.finalPrice
        );

        req.setOriginalPrice(
                pricing.originalPrice
        );

        req.setDiscount(
                pricing.discount
        );

        req.setVatAmount(
                pricing.vatAmount
        );

        req.setCouponValid(
                pricing.couponValid
        );

        req.setPricingMessage(
                pricing.message
        );

        req.setPaymentMethod(method);
        req.setLocation(location);

        req.setCustomerStatus(
                CustomerRequestStatus.REQUEST_CREATED
        );

        req.setCreatedAt(
                LocalDateTime.now()
        );

        return req;
    }
}