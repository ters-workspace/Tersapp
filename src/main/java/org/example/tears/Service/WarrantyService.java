package org.example.tears.Service;

import lombok.AllArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class WarrantyService {

    private final CarServiceRequestRepository requestRepo;
    private final WarrantyRepository warrantyRepo;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final TicketRepository ticketRepository;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final SocketService socketService;
    private final RequestMapper requestMapper;
    private final WarrantyStatusHistoryRepository warrantyHistoryRepos;
    private final CarServiceRequestService carServiceRequestService;
    private final RequestQueryService requestQueryService;


    @Transactional
    public WarrantyRequestSummaryDto createWarrantyRequest(
            Integer requestId,
            WarrantyRequestDto dto,
            List<MultipartFile> images,
            Customer customer
    ) {

        CarServiceRequest request =
                requestRepo.findById(requestId)
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        if (!request.getCustomer().getId().equals(customer.getId())) {
            throw new ApiException("غير مصرح لك");
        }

        if (!requestMapper.isWarrantyEligible(request)) {
            throw new ApiException("انتهت فترة الضمان");
        }

        if (request.getCustomerStatus() != CustomerRequestStatus.DELIVERED) {
            throw new ApiException("لا يمكن إنشاء طلب ضمان");
        }

        if (warrantyRepo.existsByRequestId(requestId)) {
            throw new ApiException("تم إنشاء طلب ضمان مسبقاً");
        }

        WarrantyRequest warranty = new WarrantyRequest();

        warranty.setRequest(request);
        warranty.setCustomer(customer);
        warranty.setWarrantyReason(dto.getWarrantyReason());
        warranty.setDescription(dto.getDescription());
        warranty.setStatus(WarrantyStatus.PENDING_REVIEW);
        warranty.setCreatedAt(LocalDateTime.now());
        warranty.setUpdatedAt(LocalDateTime.now());

        warrantyRepo.save(warranty);

        saveImages(
                warranty,
                images,
                WarrantyImageType.CUSTOMER_PROBLEM


        );

        saveCustomerWarrantyHistory(
                warranty,
                WarrantyCustomerStatus.UNDER_REVIEW
        );


        // ============================
        // إنشاء التذكرة تلقائياً
        // ============================

        Ticket ticket = new Ticket();

        ticket.setWarrantyRequest(warranty);

        ticket.setRequest(request);

        ticket.setCustomer(customer);

        ticket.setProblemType(TicketProblemType.OTHER);

        ticket.setPriority(TicketPriority.IMPORTANT);

        ticket.setStatus(TicketStatus.ACTIVE);


        ticket.setDescription(dto.getDescription());

        ticket.setCreatedAt(LocalDateTime.now());

        ticket.setUpdatedAt(LocalDateTime.now());

        ticket.setLocation(request.getLocation());

        ticket.setServiceOption(request.getServiceOption());

        ticket = ticketRepository.save(ticket);

        ticket.setTicketNumber(
                String.format("TK-%06d", ticket.getId())
        );

        ticketRepository.save(ticket);

// ============================
// WebSocket
// ============================

        socketService.send(
                "/topic/warranty/" +
                        customer.getUser().getId(),
                toWarrantyResponseDto(warranty)
        );

        socketService.send(
                "/topic/warranty-details/" +
                        warranty.getId(),
                toWarrantyDetailsDto(warranty)
        );

        socketService.send(
                "/topic/current-orders/" +
                        customer.getUser().getId(),
                carServiceRequestService.toCurrentDto(request)
        );

// ==========================================
// EMPLOYEE - LIST
// ==========================================

        Employee assignedTechnician =
                warranty.getAssignedTechnician();

        if (assignedTechnician != null &&
                assignedTechnician.getUser() != null) {

            socketService.send(
                    "/topic/employee-requests/" +
                            assignedTechnician.getUser().getId(),

                    requestQueryService.getMyRequests(
                            assignedTechnician,
                            null,
                            "ALL"
                    )
            );


            // ======================================
            // EMPLOYEE - WARRANTY DETAILS
            // ======================================

            socketService.send(
                    "/topic/employee-warranty-details/" +
                            warranty.getId(),

                    requestMapper.toEmployeeWarrantyDetailsDto(
                            warranty,
                            requestQueryService.getWarrantyTimeline(
                                    warranty.getId()
                            )
                    )
            );
        }
// ============================
// Notifications
// ============================

        String notify =
                "يوجد طلب ضمان جديد #" + ticket.getTicketNumber();

        notifyEmployees(
                EmployeeRole.SUPPORT,
                notify,
                warranty.getId()
        );

        List<User> admins =
                userRepo.findByRole(UserRole.ADMIN);

        for (User admin : admins) {

            notificationService.send(
                    admin,

                    NotificationType.WARRANTY_RECEIVED,
                    NotificationCategory.WARRANTY,

                    "طلب ضمان جديد",

                    notify,

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.WARRANTY,

                    warranty.getId().toString(),

                    NotificationSection.WARRANTY
            );
        }

        notificationService.send(
                customer.getUser(),

                NotificationType.WARRANTY_CREATED,
                NotificationCategory.WARRANTY,

                "تم استلام طلب الضمان",

                "تم استلام طلب الضمان بنجاح، وسيتم التواصل معك عبر المحادثة.",

                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.WARRANTY,
                warranty.getId().toString(),
                NotificationSection.WARRANTY
        );
        notificationService.notifyActiveAdminsAboutNewWarranty(warranty);

        return toWarrantyRequestSummaryDto(warranty);
    }

    private WarrantyRequestSummaryDto toWarrantyRequestSummaryDto(
            WarrantyRequest warranty
    ) {

        CarServiceRequest request = warranty.getRequest();

        WarrantyRequestSummaryDto dto =
                new WarrantyRequestSummaryDto();

        dto.setRequestId(
                request.getId()
        );

        dto.setOrderNumber(
                request.getOrderNumber()
        );

        dto.setPlateNumberArabic(
                requestMapper.formatArabicPlate(
                        request.getCar().getPlateNumberArabic()
                )
        );

        dto.setPlateNumberEnglish(
                requestMapper.formatEnglishPlate(
                        request.getCar().getPlateNumberEnglish()
                )
        );

        dto.setServiceType(
                request.getServiceOption().name()
        );

        // المشكلة التي كتبها العميل في طلب الضمان
        dto.setProblemDescription(
                warranty.getWarrantyReason().name()
        );

        // تاريخ إنشاء الطلب الأصلي
        dto.setCreatedAt(
                warranty.getCreatedAt()
        );


        return dto;
    }

    public void saveImages(
            WarrantyRequest warranty,
            List<MultipartFile> images,
            WarrantyImageType type
    ) {

        if (images == null) {
            return;
        }

        for (MultipartFile file : images) {

            if (file.isEmpty()) {
                throw new ApiException("يوجد ملف فارغ");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                throw new ApiException(
                        "حجم الملف لا يجب أن يتجاوز 10MB"
                );
            }

            String contentType = file.getContentType();

            if (contentType == null ||
                    !contentType.startsWith("image/")) {

                throw new ApiException(
                        "يسمح فقط برفع الصور"
                );
            }

            String fileUrl =
                    fileStorageService.saveFile(
                            file,
                            "warranty"
                    );

            WarrantyImage image =
                    new WarrantyImage();

            image.setWarrantyRequest(warranty);
            image.setImageUrl(fileUrl);
            image.setType(type);

            warranty.getImages().add(image);
        }
    }

    private void saveCustomerWarrantyHistory(
            WarrantyRequest warranty,
            WarrantyCustomerStatus customerStatus
    ) {

        WarrantyStatusHistory history =
                new WarrantyStatusHistory();

        history.setWarrantyRequest(warranty);

        history.setEmployeeStatus(null);

        history.setCustomerStatus(customerStatus);

        history.setChangedBy(null);

        history.setChangedAt(LocalDateTime.now());

        warrantyHistoryRepos.save(history);
    }

    private void saveWarrantyHistory(
            WarrantyRequest warranty,
            WarrantyStatus employeeStatus,
            WarrantyCustomerStatus customerStatus,
            Employee employee
    ) {

        WarrantyStatusHistory history =
                new WarrantyStatusHistory();

        history.setWarrantyRequest(warranty);

        history.setEmployeeStatus(employeeStatus);

        history.setCustomerStatus(customerStatus);

        history.setChangedBy(employee);

        history.setChangedAt(LocalDateTime.now());

        warrantyHistoryRepos.save(history);
    }



    @Transactional(readOnly = true)
    public List<WarrantyResponseDto> getCustomerWarrantyRequests(
            Customer customer
    ) {

        List<WarrantyRequest> requests =
                warrantyRepo.findByCustomer_IdOrderByCreatedAtDesc(customer.getId());

        List<WarrantyResponseDto> list = new ArrayList<>();

        for (WarrantyRequest w : requests) {

            WarrantyResponseDto dto = new WarrantyResponseDto();

            dto.setId(w.getId());
            dto.setOrderNumber(w.getRequest().getOrderNumber());
            dto.setWarrantyReason(w.getWarrantyReason());
            dto.setWarrantyEligibility(w.getWarrantyEligibility());
            dto.setCustomerStatus(
                    mapCustomerWarrantyStatus(
                            w.getStatus()
                    )
            );            dto.setCreatedAt(w.getCreatedAt());

            list.add(dto);
        }

        return list;
    }

    @Transactional(readOnly = true)
    public WarrantyDetailsDto details(
            Integer warrantyId,
            Customer customer
    ) {

        WarrantyRequest warranty =
                warrantyRepo.findById(warrantyId)
                        .orElseThrow(() ->
                                new ApiException("طلب الضمان غير موجود"));

        if (!warranty.getCustomer().getId().equals(customer.getId())) {
            throw new ApiException("غير مصرح لك");
        }

        WarrantyDetailsDto dto =
                new WarrantyDetailsDto();

        dto.setId(warranty.getId());

        dto.setRequestId(
                warranty.getRequest().getId()
        );

        dto.setOrderNumber(
                warranty.getRequest()
                        .getOrderNumber()
        );

        dto.setWarrantyReason(warranty.getWarrantyReason());


        dto.setDescription(
                warranty.getDescription()
        );

        dto.setStatus(
                warranty.getCustomerStatus()
        );

        dto.setRejectReason(
                warranty.getRejectReason()
        );

        dto.setCreatedAt(
                warranty.getCreatedAt()
        );
        // ===========================
// Warranty Receiving Appointment
// ===========================

        dto.setReceivingDate(
                warranty.getReceivingDate()
        );

        dto.setReceivingTime(
                warranty.getReceivingTime()
        );

        if (warranty.getReceivingLocation() != null) {
            dto.setReceivingLocation(
                    carServiceRequestService.mapLocation(warranty.getReceivingLocation())
            );
        }


// ===========================
// Warranty Delivery Appointment
// ===========================

        dto.setDeliveryDate(
                warranty.getDeliveryDate()
        );

        dto.setDeliveryTime(
                warranty.getDeliveryTime()
        );

        if (warranty.getDeliveryLocation() != null) {
            dto.setDeliveryLocation(
                    carServiceRequestService.mapLocation(warranty.getDeliveryLocation())
            );
        }

        // ===========================
        // Warranty Actions
        // ===========================

        dto.setCanChooseReceivingAppointment(
                warranty.getStatus() == WarrantyStatus.APPROVED
                        && warranty.getReceivingDate() == null
        );

        dto.setCanChooseDeliveryAppointment(
                warranty.getStatus() == WarrantyStatus.DELIVERY_IN_PROGRESS
                        && warranty.getDeliveryDate() == null
        );

        // ===========================
        // Customer
        // ===========================

        if (warranty.getCustomer() != null &&
                warranty.getCustomer().getUser() != null) {

            dto.setCustomerName(
                    warranty.getCustomer()
                            .getUser()
                            .getFullName()
            );
        }

        // ===========================
        // Car
        // ===========================

        CarServiceRequest request =
                warranty.getRequest();

        if (request != null &&
                request.getCar() != null) {

            dto.setCarModelName(
                    request.getCar()
                            .getModel()
                            .getName()
            );

            dto.setServiceOption(request.getServiceOption().name());

            dto.setCarModelNameAr(
                    request.getCar()
                            .getModel()
                            .getNameAr()
            );

            dto.setPlateNumberArabic(
                    requestMapper.formatArabicPlate(
                            request.getCar()
                                    .getPlateNumberArabic()
                    )
            );

            dto.setPlateNumberEnglish(
                    requestMapper.formatEnglishPlate(
                            request.getCar()
                                    .getPlateNumberEnglish()
                    )
            );
        }

        // ===========================
        // Warranty Description
        // ===========================

        dto.setWarrantyDescription(
                warranty.getDescription()
        );

        // ===========================
        // Images
        // ===========================

        dto.setImages(
                warranty.getImages()
                        .stream()
                        .map(img -> {

                            WarrantyImageResponseDto imageDto =
                                    new WarrantyImageResponseDto();

                            imageDto.setId(
                                    img.getId()
                            );

                            imageDto.setImageUrl(
                                    img.getImageUrl()
                            );

                            if (img.getType() != null) {

                                imageDto.setType(
                                        img.getType().name()
                                );
                            }

                            return imageDto;
                        })
                        .toList()
        );

        // ===========================
        // Timeline
        // ===========================

        dto.setTimeline(
                getCustomerWarrantyTimeline(
                        warranty.getId()
                )
        );

        return dto;
    }


    public List<CustomerWarrantyStatusHistoryDto> getCustomerWarrantyTimeline(
            Integer warrantyId
    ) {

        return warrantyHistoryRepos
                .findByWarrantyRequest_IdOrderByChangedAtAsc(warrantyId)
                .stream()
                .filter(history ->
                        history.getCustomerStatus() != null
                )
                .map(history -> {

                    CustomerWarrantyStatusHistoryDto dto =
                            new CustomerWarrantyStatusHistoryDto();

                    dto.setStatus(
                            history.getCustomerStatus()
                    );

                    dto.setChangedAt(
                            history.getChangedAt()
                    );

                    return dto;
                })
                .toList();
    }

    @Transactional
    public void approveWarranty(
            Integer warrantyId,
            Employee employee
    ) {

        WarrantyRequest warranty =
                warrantyRepo.findById(warrantyId)
                        .orElseThrow(() ->
                                new ApiException("طلب الضمان غير موجود"));

        // ============================
        // صلاحية الموظف
        // ============================

        if (employee.getEmployeeRole() != EmployeeRole.SUPPORT
                && employee.getUser().getRole() != UserRole.ADMIN) {

            throw new ApiException(
                    "غير مصرح لك بمعالجة طلبات الضمان"
            );
        }

        // ============================
        // التأكد أن الطلب لم تتم معالجته
        // ============================

        if (warranty.getStatus() != WarrantyStatus.PENDING_REVIEW) {

            throw new ApiException(
                    "تمت معالجة الطلب مسبقاً"
            );
        }

        CarServiceRequest request =
                warranty.getRequest();

        // ============================
        // الفني الذي أصلح السيارة سابقاً
        // ============================

        Employee technician =
                warranty.getRequest().getAssignedTechnician();

        if (technician == null) {
            throw new ApiException(
                    "لا يوجد فني سابق مسند لهذا الطلب"
            );
        }

        // ============================
        // الموافقة على الضمان
        // ============================

        warranty.setStatus(WarrantyStatus.APPROVED);
        warranty.setCustomerStatus(WarrantyCustomerStatus.WAITING_FOR_PICKUP_INFO);
        warranty.setWarrantyEligibility(WarrantyEligibilityStatus.COVERED);

        warranty.setApprovedBy(employee);

        warranty.setApprovedAt(
                LocalDateTime.now()
        );

        warranty.setUpdatedAt(
                LocalDateTime.now()
        );

        // نخلي الضمان مرتبط بنفس الفني
        warranty.setAssignedTechnician(
                technician
        );

        saveWarrantyHistory(
                warranty,
                WarrantyStatus.APPROVED,
                WarrantyCustomerStatus.WARRANTY_APPROVED,
                employee
        );

        // ============================
        // إعادة إسناد الطلب للفني القديم
        // ============================

        request.setAssignedTechnician(
                technician
        );

        request.setCurrentEmployee(
                technician
        );

        request.setLastUpdated(
                LocalDateTime.now()
        );

        requestRepo.save(request);

        warrantyRepo.save(warranty);

        // ============================
        // إغلاق تذكرة خدمة العملاء
        // ============================

        Ticket ticket =
                ticketRepository
                        .findByWarrantyRequest_Id(warrantyId)
                        .orElseThrow(() ->
                                new ApiException(
                                        "التذكرة غير موجودة"
                                )
                        );

        ticket.setStatus(
                TicketStatus.SOLVED
        );

        ticket.setSolvedAt(
                LocalDateTime.now()
        );

        ticket.setUpdatedAt(
                LocalDateTime.now()
        );

        ticketRepository.save(ticket);

        socketService.send(
                "/topic/warranty/" +
                        request.getCustomer()
                                .getUser()
                                .getId(),
                toWarrantyResponseDto(warranty)
        );

            socketService.send(
                    "/topic/employee-requests/" +
                            employee.getUser().getId(),
                    requestMapper.toEmployeeCardDto(
                            warranty.getRequest()
                    )
            );


        socketService.send(
                "/topic/warranty-details/" +
                        warranty.getId(),
                toWarrantyDetailsDto(warranty)
        );

        socketService.send(
                "/topic/current-orders/" +
                        request.getCustomer()
                                .getUser()
                                .getId(),
                carServiceRequestService.toCurrentDto(request)
        );


// ==========================================
// EMPLOYEE - LIST
// ==========================================

        Employee assignedTechnician =
                warranty.getAssignedTechnician();

        if (assignedTechnician != null &&
                assignedTechnician.getUser() != null) {

            socketService.send(
                    "/topic/employee-requests/" +
                            assignedTechnician.getUser().getId(),

                    requestQueryService.getMyRequests(
                            assignedTechnician,
                            null,
                            "ALL"
                    )
            );


            // ======================================
            // EMPLOYEE - WARRANTY DETAILS
            // ======================================

            socketService.send(
                    "/topic/employee-warranty-details/" +
                            warranty.getId(),

                    requestMapper.toEmployeeWarrantyDetailsDto(
                            warranty,
                            requestQueryService.getWarrantyTimeline(
                                    warranty.getId()
                            )
                    )
            );
        }

        // ============================
        // إشعار الفني
        // ============================

        notificationService.send(
                technician.getUser(),

                NotificationType.WARRANTY_APPROVED,
                NotificationCategory.WARRANTY,

                "تم إسناد طلب ضمان",

                "تمت الموافقة على طلب الضمان للطلب #"
                        + request.getOrderNumber()
                        + " وتم إسناد الطلب لك لإكمال إجراء الضمان.",

                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.WARRANTY,
                warranty.getId().toString(),
                NotificationSection.WARRANTY
        );

        // ============================
        // إشعار العميل
        // ============================

        notificationService.send(
                request.getCustomer().getUser(),

                NotificationType.WARRANTY_APPROVED,
                NotificationCategory.WARRANTY,

                "تمت الموافقة على الضمان",

                "تمت الموافقة على طلب الضمان للطلب #"
                        + request.getOrderNumber(),

                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.WARRANTY,
                warranty.getId().toString(),
                NotificationSection.WARRANTY
        );
    }

    @Transactional
    public void rejectWarranty(
            Integer warrantyId,
            Employee employee,
            String reason
    ){

        WarrantyRequest warranty =
                warrantyRepo.findById(warrantyId)
                        .orElseThrow(() ->
                                new ApiException("طلب الضمان غير موجود"));

        if (employee.getEmployeeRole() != EmployeeRole.SUPPORT
                && employee.getUser().getRole() != UserRole.ADMIN) {
            throw new ApiException("غير مصرح لك بمعالجة طلبات الضمان");
        }

        if(warranty.getStatus()!=WarrantyStatus.PENDING_REVIEW){
            throw new ApiException("تمت معالجة الطلب مسبقاً");
        }

        warranty.setStatus(WarrantyStatus.REJECTED);
        warranty.setCustomerStatus(WarrantyCustomerStatus.REJECTED);
        warranty.setWarrantyEligibility(WarrantyEligibilityStatus.NOT_COVERED);

        warranty.setApprovedBy(employee);

        warranty.setApprovedAt(LocalDateTime.now());

        warranty.setRejectReason(reason);

        saveWarrantyHistory(
                warranty,
                WarrantyStatus.REJECTED,
                WarrantyCustomerStatus.REJECTED,
                employee
        );

        warranty.setUpdatedAt(LocalDateTime.now());
        warrantyRepo.save(warranty);

        Ticket ticket =
                ticketRepository.findByWarrantyRequest_Id(warrantyId)
                        .orElseThrow(() ->
                                new ApiException("التذكرة غير موجودة"));

        ticket.setStatus(TicketStatus.SOLVED);
        ticket.setSolvedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        ticketRepository.save(ticket);
        // ============================
        // WebSocket
        // ============================

        socketService.send(
                "/topic/warranty/" +
                        warranty.getCustomer()
                                .getUser()
                                .getId(),
                toWarrantyResponseDto(warranty)
        );

        socketService.send(
                "/topic/warranty-details/" +
                        warranty.getId(),
                toWarrantyDetailsDto(warranty)
        );

        socketService.send(
                "/topic/current-orders/" +
                        warranty.getCustomer()
                                .getUser()
                                .getId(),
                carServiceRequestService.toCurrentDto(
                        warranty.getRequest()
                )
        );

        socketService.send(
                "/topic/past-orders/" + warranty.getRequest().getCustomer().getUser().getId(),
                carServiceRequestService.toHistoryDto(warranty.getRequest())
        );


// ==========================================
// EMPLOYEE - LIST
// ==========================================

        Employee assignedTechnician =
                warranty.getAssignedTechnician();

        if (assignedTechnician != null &&
                assignedTechnician.getUser() != null) {

            socketService.send(
                    "/topic/employee-requests/" +
                            assignedTechnician.getUser().getId(),

                    requestQueryService.getMyRequests(
                            assignedTechnician,
                            null,
                            "ALL"
                    )
            );


            // ======================================
            // EMPLOYEE - WARRANTY DETAILS
            // ======================================

            socketService.send(
                    "/topic/employee-warranty-details/" +
                            warranty.getId(),

                    requestMapper.toEmployeeWarrantyDetailsDto(
                            warranty,
                            requestQueryService.getWarrantyTimeline(
                                    warranty.getId()
                            )
                    )
            );
        }

        notificationService.send(
                warranty.getRequest().getCustomer().getUser(),

                NotificationType.WARRANTY_REJECTED,
                NotificationCategory.WARRANTY,

                "تم رفض طلب الضمان",

                "تم رفض طلب الضمان للطلب #"
                        + warranty.getRequest().getOrderNumber()
                        + "\nالسبب: "
                        + reason,

                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.WARRANTY,
                warranty.getId().toString(),
                NotificationSection.WARRANTY
        );

    }

    private void notifyEmployees(
            EmployeeRole role,
            String message,
            Integer warrantyId
    ) {

        List<Employee> employees =
                employeeRepo.findByEmployeeRole(role);

        for (Employee employee : employees) {

            notificationService.send(
                    employee.getUser(),

                    NotificationType.WARRANTY_RECEIVED,
                    NotificationCategory.WARRANTY,

                    "طلب ضمان جديد",

                    message,

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.WARRANTY,

                    warrantyId.toString(),

                    NotificationSection.WARRANTY
            );
        }
    }

    public List<WarrantyImageResponseDto> getWarrantyImages(
            Integer warrantyId,
            Integer customerId
    ) {

        WarrantyRequest warranty =
                warrantyRepo.findById(warrantyId)
                        .orElseThrow(() ->
                                new ApiException("طلب الضمان غير موجود"));

        if (!warranty.getCustomer().getId().equals(customerId)) {
            throw new ApiException("غير مصرح");
        }

        return warranty.getImages()
                .stream()
                .map(image -> {

                    WarrantyImageResponseDto dto =
                            new WarrantyImageResponseDto();

                    dto.setId(image.getId());
                    dto.setImageUrl(image.getImageUrl());

                    if (image.getType() != null) {
                        dto.setType(image.getType().name());
                    }

                    return dto;
                })
                .toList();
    }

    public WarrantyDetailsDto toWarrantyDetailsDto(
            WarrantyRequest warranty
    ) {

        WarrantyDetailsDto dto =
                new WarrantyDetailsDto();

        dto.setId(warranty.getId());

        dto.setOrderNumber(
                warranty.getRequest().getOrderNumber()
        );

        dto.setWarrantyReason(warranty.getWarrantyReason());


        dto.setDescription(
                warranty.getDescription()
        );

        dto.setStatus(
                warranty.getCustomerStatus()
        );

        dto.setRejectReason(
                warranty.getRejectReason()
        );

        dto.setCreatedAt(
                warranty.getCreatedAt()
        );

        if (warranty.getCustomer() != null &&
                warranty.getCustomer().getUser() != null) {

            dto.setCustomerName(
                    warranty.getCustomer()
                            .getUser()
                            .getFullName()
            );
        }

        CarServiceRequest request =
                warranty.getRequest();


        if (request != null &&
                request.getCar() != null) {

            dto.setCarModelName(
                    request.getCar()
                            .getModel()
                            .getName()
            );

            dto.setCarModelNameAr(
                    request.getCar()
                            .getModel()
                            .getNameAr()
            );

            dto.setPlateNumberArabic(
                    requestMapper.formatArabicPlate(
                            request.getCar()
                                    .getPlateNumberArabic()
                    )
            );

            dto.setPlateNumberEnglish(
                    requestMapper.formatEnglishPlate(
                            request.getCar()
                                    .getPlateNumberEnglish()
                    )
            );

            dto.setServiceOption(request.getServiceOption().name());
        }



        dto.setWarrantyDescription(
                warranty.getDescription()
        );

        dto.setImages(
                warranty.getImages()
                        .stream()
                        .map(img -> {

                            WarrantyImageResponseDto imageDto =
                                    new WarrantyImageResponseDto();

                            imageDto.setId(img.getId());

                            imageDto.setImageUrl(
                                    img.getImageUrl()
                            );

                            if (img.getType() != null) {
                                imageDto.setType(
                                        img.getType().name()
                                );
                            }

                            return imageDto;
                        })
                        .toList()
        );

        dto.setTimeline(
                getCustomerWarrantyTimeline(
                        warranty.getId()
                )
        );

        dto.setVehicleReceived(warranty.isVehicleReceived());

        return dto;
    }

    public WarrantyResponseDto toWarrantyResponseDto(
            WarrantyRequest warranty
    ) {

        WarrantyResponseDto dto =
                new WarrantyResponseDto();

        dto.setId(warranty.getId());

        dto.setOrderNumber(
                warranty.getRequest().getOrderNumber()
        );

        dto.setWarrantyReason(warranty.getWarrantyReason());

        dto.setCustomerStatus(
                mapCustomerWarrantyStatus(
                        warranty.getStatus()
                )
        );

        dto.setCreatedAt(
                warranty.getCreatedAt()
        );

        return dto;
    }
    private WarrantyCustomerStatus mapCustomerWarrantyStatus(
            WarrantyStatus status
    ) {

        return switch (status) {

            case REQUEST_SENT ->
                    WarrantyCustomerStatus.SUBMITTED;

            case PENDING_REVIEW ->
                    WarrantyCustomerStatus.UNDER_REVIEW;

            case APPROVED ->
                    WarrantyCustomerStatus.WAITING_FOR_PICKUP_INFO;

            case RECEIVED ->
                    WarrantyCustomerStatus.VEHICLE_RECEIVED;

            case INSPECTION,
                 REPAIRING,
                 TESTING ->
                    WarrantyCustomerStatus.VEHICLE_RECEIVED;

            case DELIVERY_IN_PROGRESS ->
                    WarrantyCustomerStatus.VEHICLE_READY;

            case DELIVERED ->
                    WarrantyCustomerStatus.DELIVERED;

            case REJECTED ->
                    WarrantyCustomerStatus.REJECTED;
        };
    }



}
