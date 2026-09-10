package org.example.tears.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;

    private final CarServiceRequestRepository requestRepository;
    private final NotificationService notificationService;
    private final ChatMessageRepository chatMessageRepository;

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepository;

    private final AuthService authService;
    private final ChatService chatService;
    private final SocketService socketService;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public TicketResponseDto createTicket(
            HttpServletRequest httpRequest,
            CreateTicketDto dto
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        if (user.getEmployee() == null) {
            throw new ApiException("غير مصرح لك");
        }

        CarServiceRequest request =
                requestRepository.findById(dto.getRequestId())
                        .orElseThrow(() ->
                                new ApiException("الطلب غير موجود"));

        Employee employee = user.getEmployee();

        // التحقق أن الموظف معيّن على الطلب
        boolean isAssignedEmployee =
                employee.equals(request.getAssignedTechnician())
                        || employee.equals(request.getAssignedPricingEmployee())
                        || employee.equals(request.getAssignedSupportEmployee());

        if (!isAssignedEmployee) {
            throw new ApiException("غير مصرح لك بإنشاء تذكرة لهذا الطلب");
        }

        Optional<Ticket> activeTicket =
                ticketRepository
                        .findByRequest_IdAndCreatedByEmployee_IdAndStatusIn(
                                request.getId(),
                                employee.getId(),
                                List.of(
                                        TicketStatus.ACTIVE,
                                        TicketStatus.IN_PROGRESS
                                )
                        );

        if (activeTicket.isPresent()) {
            throw new ApiException("لديك تذكرة مفتوحة لهذا الطلب");
        }

        Ticket ticket = new Ticket();

        ticket.setProblemType(dto.getProblemType());
        ticket.setPriority(dto.getPriority());
        ticket.setDescription(dto.getDescription());

        ticket.setStatus(TicketStatus.ACTIVE);

        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        ticket.setRequest(request);
        ticket.setCustomer(request.getCustomer());

        ticket.setCreatedByEmployee(user.getEmployee());
        ticket.setLocation(request.getLocation());
        ticket.setServiceOption(request.getServiceOption());

        // نحفظ أول مرة للحصول على الـ ID
        ticket = ticketRepository.save(ticket);

        // إنشاء رقم التذكرة
        ticket.setTicketNumber(
                String.format("TK-%06d", ticket.getId())
        );

        // حفظ رقم التذكرة
        ticket = ticketRepository.save(ticket);
        socketService.send(
                "/topic/employee-tickets/" +
                        ticket.getCreatedByEmployee().getUser().getId(),
                mapToResponse(ticket)
        );

        socketService.send(
                "/topic/support-tickets",
                toListDto(ticket)
        );

        long active =
                ticketRepository.countByStatus(TicketStatus.ACTIVE);

        long inProgress =
                ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);

        long solved =
                ticketRepository.countByStatus(TicketStatus.SOLVED);

        TicketSupportCountDto countDto =
                new TicketSupportCountDto(
                        active,
                        inProgress,
                        solved
                );

        socketService.send(
                "/topic/support-ticket-count",
                countDto
        );

        return mapToResponse(ticket);
    }

    public ChatRoom getRoomByTicket(Integer ticketId, User user) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException("التذكرة غير موجودة"));

        chatService.validateUserAccess(ticket, user);

        return chatRoomRepository
                .findByTicket(ticket)
                .orElseThrow(() -> new ApiException("لا توجد محادثة"));
    }

    public TicketResponseDto mapToResponse(Ticket ticket) {

        TicketResponseDto dto = new TicketResponseDto();

        dto.setId(ticket.getId());

        dto.setTicketNumber(ticket.getTicketNumber());

        dto.setOrderNumber(ticket.getRequest().getOrderNumber());

        dto.setRequestId(ticket.getRequest().getId());

        dto.setProblemType(ticket.getProblemType());

        dto.setPriority(ticket.getPriority());

        dto.setStatus(ticket.getStatus());

        dto.setCreatedAt(ticket.getCreatedAt());

        dto.setCarModel( ticket.getRequest() .getCar() .getModel() .getNameAr() );

        dto.setPlateArabic(
                formatCarArTitle(ticket.getRequest().getCar())
        );

        dto.setPlateEnglish(
                formatCarEnTitle(ticket.getRequest().getCar())
        );

        return dto;
    }
    public List<TicketResponseDto> getMyTickets(
            HttpServletRequest httpRequest
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        if (user.getEmployee() == null) {
            throw new ApiException("غير مصرح لك");
        }

        return ticketRepository
                .findByCreatedByEmployee_IdOrderByCreatedAtDesc(
                        user.getEmployee().getId()
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public TicketDetailsDto getTicketDetails(Integer ticketId) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ApiException("التذكرة غير موجودة"));

        return mapToDetails(ticket);
    }

    private TicketDetailsDto mapToDetails(Ticket ticket) {

        TicketDetailsDto dto = new TicketDetailsDto();

        dto.setId(ticket.getId());

        dto.setTicketNumber(ticket.getTicketNumber());

        if (ticket.getServiceOption() != null){
            dto.setServiceOption(ticket.getServiceOption().name());
        }

        dto.setRequestId(ticket.getRequest().getId());

        dto.setOrderNumber(ticket.getRequest().getOrderNumber());

        dto.setCustomerName(
                ticket.getCustomer()
                        .getUser()
                        .getFullName()
        );

        dto.setCustomerPhone(
                ticket.getCustomer()
                        .getUser()
                        .getPhoneNumber()
        );


        dto.setProblemType(ticket.getProblemType());

        dto.setPriority(ticket.getPriority());

        dto.setStatus(ticket.getStatus());

        dto.setAcceptedByCustomerService(
                ticket.getAcceptedByCustomerService()
        );

        dto.setCarModel( ticket.getRequest() .getCar() .getModel() .getNameAr() );

        dto.setPlateArabic(
                formatCarArTitle(ticket.getRequest().getCar())
        );

        dto.setPlateEnglish(
                formatCarEnTitle(ticket.getRequest().getCar())
        );

        dto.setAcceptedAt(
                ticket.getAcceptedAt()
        );

        dto.setSolvedAt(
                ticket.getSolvedAt()
        );

        if (ticket.getLocation() != null) {

            dto.setAddress(ticket.getLocation().getAddress());

            dto.setCity(
                    extractCity(ticket.getLocation().getAddress())
            );
        }

        if (ticket.getAssignedSupportEmployee() != null) {

            dto.setAssignedSupportEmployeeName(
                    ticket.getAssignedSupportEmployee()
                            .getUser()
                            .getFullName()
            );

            dto.setAssignedSupportEmployeePhone(
                    ticket.getAssignedSupportEmployee()
                            .getUser()
                            .getPhoneNumber()
            );
        }

        dto.setDescription(ticket.getDescription());

        dto.setCreatedAt(ticket.getCreatedAt());

        dto.setUpdatedAt(ticket.getUpdatedAt());

        dto.setSolvedAt(ticket.getSolvedAt());

        if (ticket.getAssignedSupportEmployee() != null) {

            dto.setAssignedSupportEmployee(
                    ticket.getAssignedSupportEmployee()
                            .getUser()
                            .getFullName()
            );
        }

        return dto;
    }

    private String extractCity(String address) {

        if (address == null || address.isBlank()) {
            return address;
        }

        String[] parts = address.split("،");

        return parts[parts.length - 1].trim();
    }

    private String formatCarArTitle(Car car) {

        if (car == null) {
            return null;
        }

        String model = car.getModel().getNameAr();

        String plate = car.getPlateNumberArabic();

        if (plate == null || plate.isBlank()) {
            return model;
        }

        String[] parts = plate.trim().split("\\s+");

        String letters;

        if (parts.length >= 3) {
            letters = parts[0] + " " + parts[1] + " " + parts[2];
        } else {
            letters = plate;
        }

        return model + " - " + letters;
    }


    private String formatCarEnTitle(Car car) {

        if (car == null) {
            return null;
        }

        String model = car.getModel().getName();

        String plate = car.getPlateNumberEnglish();

        if (plate == null || plate.isBlank()) {
            return model;
        }

        String letters;

        if (plate.length() >= 3) {
            letters = plate.substring(0, 3);
        } else {
            letters = plate;
        }

        return model + " - " + letters;
    }


    @Transactional
    public void updateStatus(
            Integer ticketId,
            UpdateTicketStatusDto dto,
            HttpServletRequest httpRequest
    ) {

        User user = authService.getAuthenticatedUser(httpRequest);

        if (user.getEmployee() == null) {
            throw new ApiException("غير مصرح");
        }

        Employee employee = user.getEmployee();

        if (employee.getEmployeeRole() != EmployeeRole.SUPPORT) {
            throw new ApiException("هذه العملية خاصة بخدمة العملاء");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ApiException("التذكرة غير موجودة"));

        CarServiceRequest serviceRequest = ticket.getRequest();

        TicketStatus newStatus = dto.getStatus();

        // ============================
        // ACTIVE -> IN_PROGRESS
        // ============================

        if (ticket.getStatus() == TicketStatus.ACTIVE &&
                newStatus == TicketStatus.IN_PROGRESS) {

            if (ticket.getAssignedSupportEmployee() != null) {
                throw new ApiException("تم استلام التذكرة بواسطة موظف آخر");
            }

            ChatRoom room = chatService.createRoomIfNotExists(ticket);


            User admin = userRepo.findById(8)
                    .orElseThrow(() -> new ApiException("Admin غير موجود"));

            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setChatRoom(room);
            systemMessage.setSender(admin);
            systemMessage.setType(MessageType.SYSTEM);
            systemMessage.setMessage("تم إنشاء المحادثة الخاصة بهذه التذكرة.");
            systemMessage.setCreatedAt(LocalDateTime.now());
            systemMessage.setReadStatus(ReadStatus.SENT);

            chatMessageRepository.save(systemMessage);
            chatService.sendSystemMessage(
                    room,
                    "تم إنشاء المحادثة"
            );

            // التذكرة
            ticket.setAssignedSupportEmployee(employee);
            ticket.setAcceptedByCustomerService(true);
            ticket.setAcceptedAt(LocalDateTime.now());
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setUpdatedAt(LocalDateTime.now());

// الطلب
            serviceRequest.setAssignedSupportEmployee(employee);
            serviceRequest.setCurrentEmployee(employee);
            serviceRequest.setLastUpdated(LocalDateTime.now());

            requestRepository.save(serviceRequest);
            ticketRepository.save(ticket);

            socketService.send(
                    "/topic/employee-tickets/" +
                            ticket.getCreatedByEmployee().getUser().getId(),
                    mapToResponse(ticket)
            );

            socketService.send(
                    "/topic/ticket-details/" +
                            ticket.getId(),
                    getTicketDetails(ticket.getId())
            );

            socketService.send(
                    "/topic/support-tickets",
                    toListDto(ticket)
            );
            long active =
                    ticketRepository.countByStatus(TicketStatus.ACTIVE);

            long inProgress =
                    ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);

            long solved =
                    ticketRepository.countByStatus(TicketStatus.SOLVED);

            TicketSupportCountDto countDto =
                    new TicketSupportCountDto(
                            active,
                            inProgress,
                            solved
                    );

            socketService.send(
                    "/topic/support-ticket-count",
                    countDto
            );

// إشعار بدء المحادثة
            notificationService.send(
                    ticket.getCustomer().getUser(),
                    NotificationType.SUPPORT_TICKET_ACCEPTED,
                            NotificationCategory.SUPPORT,
                    "تم استلام طلبك",
                    "قام موظف خدمة العملاء باستلام طلب الضمان ويمكنك الآن بدء المحادثة.",
                    NotificationActionType.OPEN_SECTION,
                    NotificationEntityType.SUPPORT_TICKET,
                    ticket.getId().toString(),
                    NotificationSection.CHAT
            );
            return;
        }

        // ============================
        // IN_PROGRESS -> SOLVED
        // ============================

        if (ticket.getStatus() == TicketStatus.IN_PROGRESS &&
                newStatus == TicketStatus.SOLVED) {

            if (ticket.getAssignedSupportEmployee() == null ||
                    !ticket.getAssignedSupportEmployee().getId().equals(employee.getId())) {

                throw new ApiException("ليست التذكرة الخاصة بك");
            }

            ticket.setStatus(TicketStatus.SOLVED);
            ticket.setSolvedAt(LocalDateTime.now());
            ticket.setUpdatedAt(LocalDateTime.now());

            serviceRequest.setCurrentEmployee(employee);
            serviceRequest.setLastUpdated(LocalDateTime.now());

            requestRepository.save(serviceRequest);
            ticketRepository.save(ticket);

            long active =
                    ticketRepository.countByStatus(TicketStatus.ACTIVE);

            long inProgress =
                    ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);

            long solved =
                    ticketRepository.countByStatus(TicketStatus.SOLVED);

            TicketSupportCountDto countDto =
                    new TicketSupportCountDto(
                            active,
                            inProgress,
                            solved
                    );

            socketService.send(
                    "/topic/support-ticket-count",
                    countDto
            );

            socketService.send(
                    "/topic/employee-tickets/" +
                            ticket.getCreatedByEmployee().getUser().getId(),
                    mapToResponse(ticket)
            );

            socketService.send(
                    "/topic/ticket-details/" +
                            ticket.getId(),
                    getTicketDetails(ticket.getId())
            );

            socketService.send(
                    "/topic/support-tickets",
                    toListDto(ticket)
            );

            notificationService.send(
                    ticket.getCreatedByEmployee().getUser(),

                    NotificationType.CHAT_MESSAGE_RECEIVED,
                    NotificationCategory.CHAT,

                    "تم استلام التذكرة",
                    "قام موظف خدمة العملاء باستلام التذكرة ويمكنك الآن بدء المحادثة." ,

                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.CHAT,
                    ticket.getId().toString(),
                    NotificationSection.CHAT
            );

            return;
        }

        throw new ApiException("انتقال حالة غير صحيح");
    }

    public List<TicketResponseDto> searchByOrderNumber(
            String orderNumber
    ){

        return ticketRepository
                .findByRequest_OrderNumberContainingIgnoreCase(orderNumber)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TicketListDto> getSupportTickets(
            HttpServletRequest request
    ){

        User user = authService.getAuthenticatedUser(request);

        if(user.getEmployee()==null ||
                user.getEmployee().getEmployeeRole()!=EmployeeRole.SUPPORT){

            throw new ApiException("غير مصرح");
        }

        return ticketRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toListDto)
                .toList();
    }

    public TicketListDto toListDto(Ticket ticket){

        TicketListDto dto = new TicketListDto();

        dto.setId(ticket.getId());

        dto.setTicketNumber(ticket.getTicketNumber());

        dto.setOrderNumber(ticket.getRequest().getOrderNumber());

        dto.setRequestId(ticket.getRequest().getId());

        dto.setCustomerName(
                ticket.getRequest()
                        .getCustomer()
                        .getUser()
                        .getFullName()
        );

        dto.setCarModel(
                ticket.getRequest()
                        .getCar()
                        .getModel()
                        .getNameAr()
        );

        dto.setProblemType(ticket.getProblemType());

        dto.setDescription(ticket.getDescription());

        dto.setPriority(ticket.getPriority());

        dto.setStatus(ticket.getStatus());

        dto.setAcceptedByCustomerService(
                ticket.getAcceptedByCustomerService()
        );

        dto.setCreatedAt(ticket.getCreatedAt());

        if(ticket.getAssignedSupportEmployee()!=null){

            dto.setAssignedSupportEmployeeName(
                    ticket.getAssignedSupportEmployee()
                            .getUser()
                            .getFullName()
            );
        }

        if (ticket.getCreatedByEmployee() != null &&
                ticket.getCreatedByEmployee().getUser() != null) {

            dto.setCreatedByEmployeeName(
                    ticket.getCreatedByEmployee()
                            .getUser()
                            .getFullName()
            );
        }

        return dto;
    }

    public TicketSupportCountDto getSupportTicketCount(
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        if (user.getEmployee() == null ||
                user.getEmployee().getEmployeeRole() != EmployeeRole.SUPPORT) {

            throw new ApiException("غير مصرح");
        }

        long active =
                ticketRepository.countByStatus(TicketStatus.ACTIVE);

        long inProgress =
                ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);

        long solved =
                ticketRepository.countByStatus(TicketStatus.SOLVED);

        return new TicketSupportCountDto(
                active,
                inProgress,
                solved
        );
    }

    public List<EmployeeSupportListDto> getSupportEmployees(
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        if (user.getEmployee() == null ||
                user.getEmployee().getEmployeeRole() != EmployeeRole.SUPPORT) {

            throw new ApiException("غير مصرح");
        }

        return employeeRepository.findAll()
                .stream()
                .map(this::toSupportEmployeeDto)
                .toList();
    }


    public List<EmployeeSupportListDto> searchSupportEmployees(
            HttpServletRequest request,
            String q
    ) {

        User user = authService.getAuthenticatedUser(request);

        if (user.getEmployee() == null ||
                user.getEmployee().getEmployeeRole() != EmployeeRole.SUPPORT) {

            throw new ApiException("غير مصرح");
        }

        String search = q == null ? "" : q.trim().toLowerCase();

        return employeeRepository.findAll()
                .stream()
                .filter(employee -> {

                    User employeeUser = employee.getUser();

                    String fullName =
                            employeeUser.getFullName() != null
                                    ? employeeUser.getFullName().toLowerCase()
                                    : "";

                    String employeeCode =
                            employee.getEmployeeCode() != null
                                    ? employee.getEmployeeCode().toLowerCase()
                                    : "";

                    String email =
                            employeeUser.getEmail() != null
                                    ? employeeUser.getEmail().toLowerCase()
                                    : "";

                    String phone =
                            employeeUser.getPhoneNumber() != null
                                    ? employeeUser.getPhoneNumber().toLowerCase()
                                    : "";

                    String city =
                            employee.getCity() != null
                                    ? employee.getCity().name().toLowerCase()
                                    : "";

                    String jobTitle =
                            employee.getJobTitle() != null
                                    ? employee.getJobTitle().name()
                                    : "";

                    return fullName.contains(search)
                            || employeeCode.contains(search)
                            || email.contains(search)
                            || phone.contains(search)
                            || city.contains(search)
                            || jobTitle.contains(search);
                })
                .map(this::toSupportEmployeeDto)
                .toList();
    }


    private EmployeeSupportListDto toSupportEmployeeDto(
            Employee employee
    ) {

        EmployeeSupportListDto dto =
                new EmployeeSupportListDto();

        dto.setId(employee.getId());

        dto.setFullName(
                employee.getUser().getFullName()
        );

        dto.setEmployeeCode(
                employee.getEmployeeCode()
        );

        dto.setEmail(
                employee.getUser().getEmail()
        );

        dto.setPhone(
                employee.getUser().getPhoneNumber()
        );

        dto.setCity(
                employee.getCity() != null
                        ? employee.getCity().name()
                        : null
        );

        dto.setJobTitle(
                employee.getJobTitle()
        );

        return dto;
    }

    public List<TicketListDto> searchSupportTickets(
            HttpServletRequest request,
            String q
    ) {

        User user = authService.getAuthenticatedUser(request);

        if (user.getEmployee() == null ||
                user.getEmployee().getEmployeeRole() != EmployeeRole.SUPPORT) {

            throw new ApiException("غير مصرح");
        }

        String search = q == null ? "" : q.trim().toLowerCase();

        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ticket -> {

                    String ticketNumber =
                            ticket.getTicketNumber() != null
                                    ? ticket.getTicketNumber().toLowerCase()
                                    : "";

                    String orderNumber =
                            ticket.getRequest() != null &&
                                    ticket.getRequest().getOrderNumber() != null
                                    ? ticket.getRequest().getOrderNumber().toLowerCase()
                                    : "";

                    String customerName =
                            ticket.getCustomer() != null &&
                                    ticket.getCustomer().getUser() != null &&
                                    ticket.getCustomer().getUser().getFullName() != null
                                    ? ticket.getCustomer().getUser().getFullName().toLowerCase()
                                    : "";

                    String customerPhone =
                            ticket.getCustomer() != null &&
                                    ticket.getCustomer().getUser() != null &&
                                    ticket.getCustomer().getUser().getPhoneNumber() != null
                                    ? ticket.getCustomer().getUser().getPhoneNumber().toLowerCase()
                                    : "";

                    return ticketNumber.contains(search)
                            || orderNumber.contains(search)
                            || customerName.contains(search)
                            || customerPhone.contains(search);
                })
                .map(this::toListDto)
                .toList();
    }




}
