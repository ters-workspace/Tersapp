package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.Set;
import java.util.TreeSet;

import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private LocalDateTime toUtc(LocalDateTime riyadhDateTime) {

        return riyadhDateTime
                .atZone(RIYADH_ZONE)
                .withZoneSameInstant(UTC_ZONE)
                .toLocalDateTime();
    }
    private final CarServiceRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final WarrantyRepository warrantyRepository;
    private final RequestMapper requestMapper;
    private static final ZoneId RIYADH_ZONE =
            ZoneId.of("Asia/Riyadh");
    private static final ZoneId UTC_ZONE =
            ZoneOffset.UTC;
    public DashboardDto getDashboard() {

        // =========================
        // REQUESTS
        // =========================

        long totalRequests = requestRepository.count();

        System.out.println("TOTAL CAR SERVICE REQUESTS = " + totalRequests);

        List<CarServiceRequest> requests =
                requestRepository.findAll();

        System.out.println(
                "FIND ALL CAR SERVICE REQUESTS = "
                        + requests.size()
        );

        YearMonth currentMonth = YearMonth.now(RIYADH_ZONE);

        YearMonth previousMonth = currentMonth.minusMonths(1);

        LocalDateTime currentMonthStart =
                currentMonth.atDay(1).atStartOfDay();

        LocalDateTime nextMonthStart =
                currentMonth.plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();

        LocalDateTime previousMonthStart =
                previousMonth.atDay(1).atStartOfDay();

// Convert Riyadh boundaries to UTC
        LocalDateTime currentMonthStartUtc = toUtc(currentMonthStart);
        LocalDateTime nextMonthStartUtc = toUtc(nextMonthStart);
        LocalDateTime previousMonthStartUtc = toUtc(previousMonthStart);

        long currentMonthRequests =
                requestRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                currentMonthStartUtc,
                                nextMonthStartUtc
                        );

        long previousMonthRequests =
                requestRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                previousMonthStartUtc,
                                currentMonthStartUtc
                        );

        BigDecimal requestsGrowthPercentage =
                calculateGrowthPercentage(
                        currentMonthRequests,
                        previousMonthRequests
                );


        // =========================
        // EMPLOYEES
        // =========================

        List<JobTitle> employeeJobTitles =
                Arrays.asList(
                        JobTitle.TECHNICIAN,
                        JobTitle.PRICING
                );

        long activeEmployees =
                employeeRepository
                        .countByUser_StatusAndJobTitleIn(
                                UserStatus.ACTIVE,
                                employeeJobTitles
                        );

        LocalDateTime monthStart =
                currentMonthStart;

        long newEmployeesThisMonth =
                employeeRepository
                        .countByUser_StatusAndJobTitleInAndUser_CreatedAtGreaterThanEqual(
                                UserStatus.ACTIVE,
                                employeeJobTitles,
                                currentMonthStartUtc
                        );


        // =========================
        // REVENUE
        // =========================

        BigDecimal totalRevenue =
                requestRepository.sumFinalPrice();

        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }


        // =========================
        // TODAY
        // =========================

        LocalDate today = LocalDate.now(RIYADH_ZONE);

        LocalDateTime todayStart =
                today.atStartOfDay();

        LocalDateTime tomorrowStart =
                today.plusDays(1).atStartOfDay();

        LocalDateTime todayStartUtc = toUtc(todayStart);
        LocalDateTime tomorrowStartUtc = toUtc(tomorrowStart);


        // =========================
        // CUSTOMER APP
        // =========================

        long activeCustomers =
                userRepository.countByRoleAndStatus(
                        UserRole.CUSTOMER,
                        UserStatus.ACTIVE
                );

        long todayRequests =
                requestRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                todayStartUtc,
                                tomorrowStartUtc
                        );

        CustomerAppStats customerApp =
                new CustomerAppStats(
                        activeCustomers,
                        todayRequests,
                        AppStatus.READY
                );


        // =========================
        // EMPLOYEE APP
        // =========================

        long todayWorkedRequests =
                requestRepository
                        .countByStaffStatusNotAndLastUpdatedGreaterThanEqualAndLastUpdatedLessThan(
                                StaffRequestStatus.NEW,
                                todayStartUtc,
                                tomorrowStartUtc
                        );

        EmployeeAppStats employeeApp =
                new EmployeeAppStats(
                        activeEmployees,
                        todayWorkedRequests,
                        AppStatus.READY
                );


        // =========================
        // CUSTOMER SERVICE
        // =========================

        long activeCustomerServiceEmployees =
                employeeRepository
                        .countByUser_StatusAndJobTitle(
                                UserStatus.ACTIVE,
                                JobTitle.CUSTOMER_SERVICE
                        );

        long todayTickets =
                ticketRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                todayStartUtc,
                                tomorrowStartUtc
                        );

        CustomerServiceStats customerService =
                new CustomerServiceStats(
                        activeCustomerServiceEmployees,
                        todayTickets,
                        AppStatus.READY
                );


        // =========================
        // FINAL RESPONSE
        // =========================

        return new DashboardDto(
                totalRequests,
                requestsGrowthPercentage,
                activeEmployees,
                newEmployeesThisMonth,
                totalRevenue,
                customerApp,
                employeeApp,
                customerService
        );
    }

    private BigDecimal calculateGrowthPercentage(
            long current,
            long previous
    ) {

        if (previous == 0) {

            if (current == 0) {
                return BigDecimal.ZERO;
            }

            return BigDecimal.valueOf(100);
        }

        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(previous),
                        2,
                        java.math.RoundingMode.HALF_UP
                );
    }

    public EmployeeDashboardDto getEmployees(String search) {

        long totalEmployees =
                employeeRepository.countByUser_Status(UserStatus.ACTIVE);

        long technicians =
                employeeRepository.countByUser_StatusAndJobTitle(
                        UserStatus.ACTIVE,
                        JobTitle.TECHNICIAN
                );

        long pricingEmployees =
                employeeRepository.countByUser_StatusAndJobTitle(
                        UserStatus.ACTIVE,
                        JobTitle.PRICING
                );

        long customerServiceEmployees =
                employeeRepository.countByUser_StatusAndJobTitle(
                        UserStatus.ACTIVE,
                        JobTitle.CUSTOMER_SERVICE
                );

        List<Employee> employees;

        if (search == null || search.trim().isEmpty()) {
            employees = employeeRepository.findAll();
        } else {
            employees = employeeRepository.searchEmployees(search.trim());
        }

        List<EmployeeListDto> employeeList = new ArrayList<>();

        for (Employee employee : employees) {

            long completedRequests =
                    requestRepository.countCompletedRequestsByEmployee(
                            employee.getId(),
                            StaffRequestStatus.DELIVERED
                    );

            long assignedRequests =
                    requestRepository.countAssignedRequestsByEmployee(
                            employee.getId(),
                            List.of(PricingStatus.values()),
                            StaffRequestStatus.DELIVERED
                    );

            employeeList.add(
                    requestMapper.toEmployeeAdminDto(
                            employee,
                            completedRequests,
                            assignedRequests
                    )
            );
        }

        return new EmployeeDashboardDto(
                totalEmployees,
                technicians,
                pricingEmployees,
                customerServiceEmployees,
                employeeList
        );
    }
    public DashboardStatsDto getStats(
            DashboardSection section
    ) {

        switch (section) {

            case CUSTOMER:
                return getCustomerStats();

            case EMPLOYEE:
                return getEmployeeStats();

            case SUPPORT:
                return getSupportStats();

            case WARRANTY:
                return getWarrantyStats();

            case ALL:
            default:
                return getAllStats();
        }

    }

    private DashboardStatsDto getCustomerStats() {

        List<CarServiceRequest> requests =
                requestRepository.findAll();

        Map<CustomerRequestStatus, Long> counts =
                new EnumMap<>(
                        CustomerRequestStatus.class
                );

        for (CustomerRequestStatus status :
                CustomerRequestStatus.values()) {

            counts.put(status, 0L);
        }

        for (CarServiceRequest request : requests) {

            CustomerRequestStatus status =
                    request.getCustomerStatus();

            if (status != null) {

                counts.put(
                        status,
                        counts.get(status) + 1
                );
            }
        }

        List<DashboardStatusCountDto> statuses =
                new ArrayList<>();

        for (CustomerRequestStatus status :
                CustomerRequestStatus.values()) {

            statuses.add(
                    new DashboardStatusCountDto(
                            DashboardSection.CUSTOMER.name(),
                            status.name(),
                            counts.get(status)
                    )
            );
        }

        return new DashboardStatsDto(
                DashboardSection.CUSTOMER.name(),
                (long) requests.size(),
                statuses
        );
    }

    private DashboardStatsDto getEmployeeStats() {

        List<CarServiceRequest> requests =
                requestRepository.findAll();

        Map<StaffRequestStatus, Long> counts =
                new EnumMap<>(
                        StaffRequestStatus.class
                );

        for (StaffRequestStatus status :
                StaffRequestStatus.values()) {

            counts.put(status, 0L);
        }

        long total = 0;

        for (CarServiceRequest request : requests) {

            if (request.getStaffStatus() != null) {

                total++;

                StaffRequestStatus status =
                        request.getStaffStatus();

                counts.put(
                        status,
                        counts.get(status) + 1
                );
            }
        }

        List<DashboardStatusCountDto> statuses =
                new ArrayList<>();

        for (StaffRequestStatus status :
                StaffRequestStatus.values()) {

            statuses.add(
                    new DashboardStatusCountDto(
                            DashboardSection.EMPLOYEE.name(),
                            status.name(),
                            counts.get(status)
                    )
            );
        }

        return new DashboardStatsDto(
                DashboardSection.EMPLOYEE.name(),
                total,
                statuses
        );
    }

    private DashboardStatsDto getSupportStats() {

        List<Ticket> tickets =
                ticketRepository
                        .findAllByOrderByCreatedAtDesc();

        Map<TicketStatus, Long> counts =
                new EnumMap<>(
                        TicketStatus.class
                );

        for (TicketStatus status :
                TicketStatus.values()) {

            counts.put(status, 0L);
        }

        for (Ticket ticket : tickets) {

            if (ticket.getStatus() != null) {

                TicketStatus status =
                        ticket.getStatus();

                counts.put(
                        status,
                        counts.get(status) + 1
                );
            }
        }

        List<DashboardStatusCountDto> statuses =
                new ArrayList<>();

        for (TicketStatus status :
                TicketStatus.values()) {

            statuses.add(
                    new DashboardStatusCountDto(
                            DashboardSection.SUPPORT.name(),
                            status.name(),
                            counts.get(status)
                    )
            );
        }

        return new DashboardStatsDto(
                DashboardSection.SUPPORT.name(),
                (long) tickets.size(),
                statuses
        );
    }

    private DashboardStatsDto getWarrantyStats() {

        List<WarrantyRequest> requests =
                warrantyRepository.findAll();

        Map<WarrantyStatus, Long> counts =
                new EnumMap<>(
                        WarrantyStatus.class
                );

        for (WarrantyStatus status :
                WarrantyStatus.values()) {

            counts.put(status, 0L);
        }

        for (WarrantyRequest request : requests) {

            WarrantyStatus status =
                    request.getStatus();

            if (status != null) {

                counts.put(
                        status,
                        counts.get(status) + 1
                );
            }
        }

        List<DashboardStatusCountDto> statuses =
                new ArrayList<>();

        for (WarrantyStatus status :
                WarrantyStatus.values()) {

            statuses.add(
                    new DashboardStatusCountDto(
                            DashboardSection.WARRANTY.name(),
                            status.name(),
                            counts.get(status)
                    )
            );
        }

        return new DashboardStatsDto(
                DashboardSection.WARRANTY.name(),
                (long) requests.size(),
                statuses
        );
    }

    private DashboardStatsDto getAllStats() {

        DashboardStatsDto customer =
                getCustomerStats();

        DashboardStatsDto employee =
                getEmployeeStats();

        DashboardStatsDto support =
                getSupportStats();

        DashboardStatsDto warranty =
                getWarrantyStats();

        List<DashboardStatusCountDto> statuses =
                new ArrayList<>();

        statuses.addAll(customer.getStatuses());
        statuses.addAll(employee.getStatuses());
        statuses.addAll(support.getStatuses());
        statuses.addAll(warranty.getStatuses());

        long total =
                customer.getTotal()
                        + support.getTotal()
                        + warranty.getTotal();

        return new DashboardStatsDto(
                DashboardSection.ALL.name(),
                total,
                statuses
        );
    }

    public List<DashboardItemDto> getItems(
            DashboardSection section,
            String status
    ) {

        List<DashboardItemDto> items;

        switch (section) {

            case CUSTOMER:
                items = getCustomerItems();
                break;

            case EMPLOYEE:
                items = getEmployeeItems();
                break;

            case SUPPORT:
                items = getSupportItems();
                break;

            case WARRANTY:
                items = getWarrantyItems();
                break;

            case ALL:
            default:
                items = getAllItems();
                break;
        }

        if (status == null || status.isBlank()) {
            return items;
        }

        List<DashboardItemDto> filtered =
                new ArrayList<>();

        for (DashboardItemDto item : items) {

            if (status.equals(item.getStatus())) {

                filtered.add(item);
            }
        }

        return filtered;
    }

    private Employee getResponsibleEmployee(
            CarServiceRequest request
    ) {

        if (request.getCurrentEmployee() != null) {
            return request.getCurrentEmployee();
        }

        if (request.getAssignedTechnician() != null) {
            return request.getAssignedTechnician();
        }

        if (request.getAssignedPricingEmployee() != null) {
            return request.getAssignedPricingEmployee();
        }

        if (request.getAssignedSupportEmployee() != null) {
            return request.getAssignedSupportEmployee();
        }

        return null;
    }

    private DashboardItemDto mapRequest(
            CarServiceRequest request,
            String status
    ) {

        Employee employee =
                getResponsibleEmployee(request);

        DashboardItemDto dto =
                new DashboardItemDto();

        dto.setItemType("REQUEST");

        dto.setRequestId(request.getId());

        dto.setReferenceNumber(
                request.getOrderNumber()
        );

        if (request.getCustomer() != null &&
                request.getCustomer().getUser() != null) {

            dto.setCustomerName(
                    request.getCustomer()
                            .getUser()
                            .getFullName()
            );

            dto.setCustomerPhone(
                    request.getCustomer()
                            .getUser()
                            .getPhoneNumber()
            );
        }

        if (request.getServiceOption() != null) {

            dto.setServiceType(
                    request.getServiceOption().name()
            );
        }

        if (request.getFinalPrice() != null) {

            dto.setTotalAmount(
                    request.getFinalPrice().doubleValue()
            );

        } else if (request.getEstimatedPrice() != null) {

            dto.setTotalAmount(
                    request.getEstimatedPrice()
            );
        }

        dto.setCreatedAt(
                request.getCreatedAt()
        );

        if (employee != null) {

            dto.setResponsibleEmployeeId(
                    employee.getId()
            );

            if (employee.getUser() != null) {

                dto.setResponsibleEmployeeName(
                        employee.getUser()
                                .getFullName()
                );

                dto.setResponsibleEmployeeEmail(
                        employee.getUser()
                                .getEmail()
                );
            }
        }

        dto.setStatus(status);

        // يظهر زر الإسناد فقط إذا لم يتم إسناد فني للطلب
        dto.setCanAssign(
                request.getAssignedTechnician() == null
        );

        return dto;
    }

    private List<DashboardItemDto> getCustomerItems() {

        List<CarServiceRequest> requests =
                requestRepository.findAll();

        List<DashboardItemDto> items =
                new ArrayList<>();

        for (CarServiceRequest request : requests) {

            String status = null;

            if (request.getCustomerStatus() != null) {

                status =
                        request.getCustomerStatus()
                                .name();
            }

            items.add(
                    mapRequest(
                            request,
                            status
                    )
            );
        }

        return items;
    }

    private List<DashboardItemDto> getEmployeeItems() {

        List<CarServiceRequest> requests =
                requestRepository.findAll();

        List<DashboardItemDto> items =
                new ArrayList<>();

        for (CarServiceRequest request : requests) {

            String status = null;

            if (request.getStaffStatus() != null) {

                status =
                        request.getStaffStatus()
                                .name();
            }

            items.add(
                    mapRequest(
                            request,
                            status
                    )
            );
        }

        return items;
    }

    private List<DashboardItemDto> getAllItems() {

        List<DashboardItemDto> items =
                new ArrayList<>();


        // الطلبات تظهر مرة واحدة فقط
        for (CarServiceRequest request :
                requestRepository.findAll()) {

            String status = null;

            if (request.getStaffStatus() != null) {

                status =
                        request.getStaffStatus()
                                .name();

            } else if (request.getCustomerStatus() != null) {

                status =
                        request.getCustomerStatus()
                                .name();
            }

            items.add(
                    mapRequest(
                            request,
                            status
                    )
            );
        }


        // التذاكر
        items.addAll(
                getSupportItems()
        );


        // الضمان
        items.addAll(
                getWarrantyItems()
        );


        return items;
    }


    private DashboardItemDto mapTicket(
            Ticket ticket
    ) {

        DashboardItemDto dto =
                new DashboardItemDto();

        dto.setItemType("TICKET");

        dto.setTicketId(
                ticket.getId()
        );

        if (ticket.getRequest() != null) {

            dto.setRequestId(
                    ticket.getRequest()
                            .getId()
            );
        }

        dto.setReferenceNumber(
                ticket.getTicketNumber()
        );


        if (ticket.getCustomer() != null &&
                ticket.getCustomer().getUser() != null) {

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
        }


        if (ticket.getServiceOption() != null) {

            dto.setServiceType(
                    ticket.getServiceOption()
                            .name()
            );
        }


        dto.setCreatedAt(
                ticket.getCreatedAt()
        );


        Employee responsibleEmployee =
                ticket.getCreatedByEmployee();

        if (responsibleEmployee != null &&
                responsibleEmployee.getUser() != null) {

            dto.setResponsibleEmployeeId(
                    responsibleEmployee.getId()
            );

            dto.setResponsibleEmployeeName(
                    responsibleEmployee
                            .getUser()
                            .getFullName()
            );

            dto.setResponsibleEmployeeEmail(
                    responsibleEmployee
                            .getUser()
                            .getEmail()
            );
        }


        if (ticket.getStatus() != null) {

            dto.setStatus(
                    ticket.getStatus()
                            .name()
            );
        }


        dto.setCanAssign(false);

        return dto;
    }

    private List<DashboardItemDto> getSupportItems() {

        List<Ticket> tickets =
                ticketRepository
                        .findAllByOrderByCreatedAtDesc();

        List<DashboardItemDto> items =
                new ArrayList<>();

        for (Ticket ticket : tickets) {

            items.add(
                    mapTicket(ticket)
            );
        }

        return items;
    }

    private DashboardItemDto mapWarranty(
            WarrantyRequest warranty
    ) {

        DashboardItemDto dto =
                new DashboardItemDto();

        dto.setItemType("WARRANTY");

        dto.setWarrantyId(
                warranty.getId()
        );


        if (warranty.getRequest() != null) {

            dto.setRequestId(
                    warranty.getRequest()
                            .getId()
            );

            dto.setReferenceNumber(
                    warranty.getRequest()
                            .getOrderNumber()
            );
        }


        if (warranty.getCustomer() != null &&
                warranty.getCustomer().getUser() != null) {

            dto.setCustomerName(
                    warranty.getCustomer()
                            .getUser()
                            .getFullName()
            );

            dto.setCustomerPhone(
                    warranty.getCustomer()
                            .getUser()
                            .getPhoneNumber()
            );
        }


        dto.setServiceType(
                "WARRANTY"
        );


        dto.setCreatedAt(
                warranty.getCreatedAt()
        );


        Employee employee =
                warranty.getAssignedTechnician();

        if (employee != null &&
                employee.getUser() != null) {

            dto.setResponsibleEmployeeId(
                    employee.getId()
            );

            dto.setResponsibleEmployeeName(
                    employee.getUser()
                            .getFullName()
            );

            dto.setResponsibleEmployeeEmail(
                    employee.getUser()
                            .getEmail()
            );
        }


        if (warranty.getStatus() != null) {

            dto.setStatus(
                    warranty.getStatus()
                            .name()
            );
        }


        dto.setCanAssign(
                warranty.getAssignedTechnician() == null
        );

        return dto;
    }

    private List<DashboardItemDto> getWarrantyItems() {

        List<WarrantyRequest> warranties =
                warrantyRepository.findAll();

        List<DashboardItemDto> items =
                new ArrayList<>();

        for (WarrantyRequest warranty : warranties) {

            items.add(
                    mapWarranty(warranty)
            );
        }

        return items;
    }

    public DashboardContactResponseDto getContactInfo(
            String itemType,
            Integer itemId
    ) {

        List<ContactInfoDto> contacts = new ArrayList<>();

        switch (itemType.toUpperCase()) {

            case "REQUEST":
                contacts = getRequestContacts(itemId);
                break;

            case "TICKET":
                contacts = getTicketContacts(itemId);
                break;

            case "WARRANTY":
                contacts = getWarrantyContacts(itemId);
                break;

            default:
                throw new ApiException("Invalid item type");
        }

        return new DashboardContactResponseDto(
                itemType.toUpperCase(),
                contacts
        );
    }

    private List<ContactInfoDto> getRequestContacts(Integer requestId) {

        CarServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException("Request not found"));

        List<ContactInfoDto> contacts = new ArrayList<>();

        // العميل
        Customer customer = request.getCustomer();

        if (customer != null && customer.getUser() != null) {

            User user = customer.getUser();

            contacts.add(new ContactInfoDto(
                    "CUSTOMER",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    "العميل"
            ));
        }

        // الموظف المسؤول الحالي
        Employee employee = request.getCurrentEmployee();

        if (employee == null) {
            employee = request.getAssignedTechnician();
        }

        if (employee == null) {
            employee = request.getAssignedPricingEmployee();
        }

        if (employee == null) {
            employee = request.getAssignedSupportEmployee();
        }

        if (employee != null && employee.getUser() != null) {

            User user = employee.getUser();

            contacts.add(new ContactInfoDto(
                    "EMPLOYEE",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    getEmployeeRole(employee)
            ));
        }

        return contacts;
    }

    private String getEmployeeRole(Employee employee) {

        if (employee.getJobTitle() == null) {
            return "موظف";
        }

        switch (employee.getJobTitle()) {

            case TECHNICIAN:
                return "موظف الصيانة";

            case PRICING:
                return "موظف التسعير";

            case CUSTOMER_SERVICE:
                return "خدمة العملاء";

            default:
                return "موظف";
        }
    }

    private List<ContactInfoDto> getTicketContacts(Integer ticketId) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException("Ticket not found"));

        List<ContactInfoDto> contacts = new ArrayList<>();

        // تذكرة مرتبطة بالضمان
        if (ticket.getWarrantyRequest() != null) {

            Customer customer = ticket.getCustomer();

            if (customer != null && customer.getUser() != null) {

                User user = customer.getUser();

                contacts.add(new ContactInfoDto(
                        "CUSTOMER",
                        user.getFullName(),
                        user.getPhoneNumber(),
                        user.getEmail(),
                        "العميل"
                ));
            }

            Employee support = ticket.getAssignedSupportEmployee();

            if (support != null && support.getUser() != null) {

                User user = support.getUser();

                contacts.add(new ContactInfoDto(
                        "EMPLOYEE",
                        user.getFullName(),
                        user.getPhoneNumber(),
                        user.getEmail(),
                        "خدمة العملاء"
                ));
            }

            return contacts;
        }

        // تذكرة عادية بين الموظف وخدمة العملاء

        Employee createdBy = ticket.getCreatedByEmployee();

        if (createdBy != null && createdBy.getUser() != null) {

            User user = createdBy.getUser();

            contacts.add(new ContactInfoDto(
                    "EMPLOYEE",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    getEmployeeRole(createdBy)
            ));
        }

        Employee support = ticket.getAssignedSupportEmployee();

        if (support != null && support.getUser() != null) {

            User user = support.getUser();

            contacts.add(new ContactInfoDto(
                    "EMPLOYEE",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    "خدمة العملاء"
            ));
        }

        return contacts;
    }

    private List<ContactInfoDto> getWarrantyContacts(Integer warrantyId) {

        WarrantyRequest warranty = warrantyRepository.findById(warrantyId)
                .orElseThrow(() -> new ApiException("Warranty request not found"));

        List<ContactInfoDto> contacts = new ArrayList<>();

        // العميل
        Customer customer = warranty.getCustomer();

        if (customer != null && customer.getUser() != null) {

            User user = customer.getUser();

            contacts.add(new ContactInfoDto(
                    "CUSTOMER",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    "العميل"
            ));
        }

        // الفني المسؤول
        Employee technician = warranty.getAssignedTechnician();

        if (technician != null && technician.getUser() != null) {

            User user = technician.getUser();

            contacts.add(new ContactInfoDto(
                    "EMPLOYEE",
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    "موظف الصيانة"
            ));
        }

        return contacts;
    }

    public StatisticsDto getStatistics(
            String calendar,
            String view,
            Integer year,
            Integer month
    ) {

        calendar = calendar.toLowerCase(Locale.ROOT);
        view = view.toLowerCase(Locale.ROOT);

        if (!calendar.equals("hijri") && !calendar.equals("gregorian")) {
            throw new IllegalArgumentException(
                    "Calendar must be hijri or gregorian"
            );
        }

        if (!view.equals("months") && !view.equals("years")) {
            throw new IllegalArgumentException(
                    "View must be months or years"
            );
        }

        // Current date according to Riyadh timezone
        LocalDate today = LocalDate.now(RIYADH_ZONE);
        HijrahDate todayHijri = HijrahDate.now(RIYADH_ZONE);

        if (year == null) {
            year = calendar.equals("hijri")
                    ? todayHijri.get(ChronoField.YEAR_OF_ERA)
                    : today.getYear();
        }

        if (view.equals("months")) {

            if (month == null) {
                month = calendar.equals("hijri")
                        ? todayHijri.get(ChronoField.MONTH_OF_YEAR)
                        : today.getMonthValue();
            }

            if (month < 1 || month > 12) {
                throw new IllegalArgumentException(
                        "Month must be between 1 and 12"
                );
            }

        } else {
            month = null;
        }

        // Selected period in Riyadh timezone
        LocalDateTime currentStart =
                getPeriodStart(calendar, view, year, month);

        LocalDateTime currentEnd =
                getPeriodEnd(calendar, view, year, month);

        // Convert selected period to UTC for database queries
        LocalDateTime currentStartUtc = toUtc(currentStart);
        LocalDateTime currentEndUtc = toUtc(currentEnd);

        // Previous period
        int previousYear = year;
        Integer previousMonth = month;

        if (view.equals("months")) {

            if (calendar.equals("hijri")) {

                HijrahDate previousHijriMonth =
                        HijrahDate.of(year, month, 1)
                                .minus(1, ChronoUnit.MONTHS);

                previousYear =
                        previousHijriMonth.get(ChronoField.YEAR_OF_ERA);

                previousMonth =
                        previousHijriMonth.get(ChronoField.MONTH_OF_YEAR);

            } else {

                YearMonth previousGregorianMonth =
                        YearMonth.of(year, month).minusMonths(1);

                previousYear = previousGregorianMonth.getYear();
                previousMonth = previousGregorianMonth.getMonthValue();
            }

        } else {
            previousYear = year - 1;
        }

        LocalDateTime previousStart =
                getPeriodStart(
                        calendar,
                        view,
                        previousYear,
                        previousMonth
                );

        LocalDateTime previousEnd =
                getPeriodEnd(
                        calendar,
                        view,
                        previousYear,
                        previousMonth
                );

        // Convert previous period to UTC
        LocalDateTime previousStartUtc = toUtc(previousStart);
        LocalDateTime previousEndUtc = toUtc(previousEnd);

        // Orders
        long currentOrders =
                requestRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        currentStartUtc,
                        currentEndUtc
                );

        long previousOrders =
                requestRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        previousStartUtc,
                        previousEndUtc
                );

        BigDecimal ordersGrowthPercentage =
                calculateGrowthPercentage(
                        currentOrders,
                        previousOrders
                );

        // Customers
        long activeCustomers =
                userRepository.countByRoleAndStatus(
                        UserRole.CUSTOMER,
                        UserStatus.ACTIVE
                );

        long newCustomers =
                userRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        UserRole.CUSTOMER,
                        currentStartUtc,
                        currentEndUtc
                );

        // Revenue
        BigDecimal revenue =
                requestRepository.sumFinalPriceBetween(
                        currentStartUtc,
                        currentEndUtc
                );

        BigDecimal previousRevenue =
                requestRepository.sumFinalPriceBetween(
                        previousStartUtc,
                        previousEndUtc
                );

        BigDecimal revenueGrowthPercentage =
                calculateGrowthPercentage(
                        revenue,
                        previousRevenue
                );

        // Monthly chart
        List<MonthlyStatisticsDto> monthlyStatistics =
                getMonthlyStatistics(calendar, year);

        // Hourly chart according to Riyadh timezone
        List<HourlyOrdersDto> hourlyOrders =
                getHourlyOrders(currentStart, currentEnd);

        // Years that contain orders according to selected calendar
        List<Integer> availableYears =
                getAvailableYears(calendar);

        // Period information remains in Riyadh dates
        PeriodDto period = new PeriodDto(
                calendar,
                view,
                year,
                month,
                currentStart.toLocalDate().toString(),
                currentEnd.minusDays(1).toLocalDate().toString()
        );

        return new StatisticsDto(
                currentOrders,
                ordersGrowthPercentage,
                revenue,
                revenueGrowthPercentage,
                newCustomers,
                activeCustomers,
                monthlyStatistics,
                hourlyOrders,
                availableYears,
                period
        );
    }
    private LocalDateTime getPeriodStart(
            String calendar,
            String view,
            int year,
            Integer month
    ) {

        if (calendar.equals("hijri")) {

            if (view.equals("months")) {
                return getHijriMonthStart(year, month);
            }

            HijrahDate startDate =
                    HijrahDate.of(year, 1, 1);

            return LocalDate.ofEpochDay(
                    startDate.toEpochDay()
            ).atStartOfDay();
        }

        if (view.equals("months")) {

            return YearMonth.of(year, month)
                    .atDay(1)
                    .atStartOfDay();
        }

        return LocalDate.of(year, 1, 1).atStartOfDay();
    }


    private LocalDateTime getPeriodEnd(
            String calendar,
            String view,
            int year,
            Integer month
    ) {

        if (calendar.equals("hijri")) {

            if (view.equals("months")) {
                return getHijriMonthEnd(year, month);
            }

            HijrahDate nextYear =
                    HijrahDate.of(year + 1, 1, 1);

            return LocalDate.ofEpochDay(
                    nextYear.toEpochDay()
            ).atStartOfDay();
        }

        if (view.equals("months")) {

            return YearMonth.of(year, month)
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay();
        }

        return LocalDate.of(year + 1, 1, 1).atStartOfDay();
    }


    private LocalDateTime getHijriMonthStart(
            int hijriYear,
            int hijriMonth
    ) {

        HijrahDate hijriDate =
                HijrahDate.of(hijriYear, hijriMonth, 1);

        LocalDate date =
                LocalDate.ofEpochDay(hijriDate.toEpochDay());

        return date.atStartOfDay();
    }


    private LocalDateTime getHijriMonthEnd(
            int hijriYear,
            int hijriMonth
    ) {

        HijrahDate nextMonth =
                HijrahDate.of(hijriYear, hijriMonth, 1)
                        .plus(1, ChronoUnit.MONTHS);

        LocalDate date =
                LocalDate.ofEpochDay(nextMonth.toEpochDay());

        return date.atStartOfDay();
    }

    private List<MonthlyStatisticsDto> getMonthlyStatistics(
            String calendar,
            int year
    ) {

        List<MonthlyStatisticsDto> statistics = new ArrayList<>();

        String[] monthNames = calendar.equals("hijri")
                ? HIJRI_MONTH_NAMES
                : GREGORIAN_MONTH_NAMES;

        for (int month = 1; month <= 12; month++) {

            // Get month boundaries in Riyadh timezone
            LocalDateTime start =
                    getPeriodStart(calendar, "months", year, month);

            LocalDateTime end =
                    getPeriodEnd(calendar, "months", year, month);

            // Convert boundaries to UTC
            LocalDateTime startUtc = toUtc(start);
            LocalDateTime endUtc = toUtc(end);

            long orders =
                    requestRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            startUtc,
                            endUtc
                    );

            BigDecimal revenue =
                    requestRepository.sumFinalPriceBetween(
                            startUtc,
                            endUtc
                    );

            statistics.add(
                    new MonthlyStatisticsDto(
                            month,
                            monthNames[month - 1],
                            orders,
                            revenue
                    )
            );
        }

        return statistics;
    }

    private static final String[] HIJRI_MONTH_NAMES = {
            "محرم",
            "صفر",
            "ربيع الأول",
            "ربيع الآخر",
            "جمادى الأولى",
            "جمادى الآخرة",
            "رجب",
            "شعبان",
            "رمضان",
            "شوال",
            "ذو القعدة",
            "ذو الحجة"
    };
    private static final String[] GREGORIAN_MONTH_NAMES = {
            "يناير",
            "فبراير",
            "مارس",
            "أبريل",
            "مايو",
            "يونيو",
            "يوليو",
            "أغسطس",
            "سبتمبر",
            "أكتوبر",
            "نوفمبر",
            "ديسمبر"
    };
    private List<HourlyOrdersDto> getHourlyOrders(
            LocalDateTime start,
            LocalDateTime end
    ) {

        LocalDateTime startUtc = toUtc(start);
        LocalDateTime endUtc = toUtc(end);

        List<Object[]> results =
                requestRepository.countOrdersByHour(
                        startUtc,
                        endUtc
                );

        Map<Integer, Long> ordersByHour = new HashMap<>();

        for (Object[] result : results) {

            Integer hour = ((Number) result[0]).intValue();
            Long count = ((Number) result[1]).longValue();

            ordersByHour.put(hour, count);
        }

        List<HourlyOrdersDto> hourlyOrders = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {

            long orders = ordersByHour.getOrDefault(hour, 0L);

            String time = String.format("%02d:00", hour);

            hourlyOrders.add(
                    new HourlyOrdersDto(
                            hour,
                            time,
                            orders
                    )
            );
        }

        return hourlyOrders;
    }

    private List<Integer> getAvailableYears(String calendar) {

        List<LocalDateTime> createdDates =
                requestRepository.findAllCreatedAt();

        Set<Integer> years = new TreeSet<>(Comparator.reverseOrder());

        for (LocalDateTime createdAt : createdDates) {

            if (createdAt == null) {
                continue;
            }

            // Database timestamp is stored in UTC
            LocalDate riyadhDate = createdAt
                    .atZone(UTC_ZONE)
                    .withZoneSameInstant(RIYADH_ZONE)
                    .toLocalDate();

            int year;

            if (calendar.equals("hijri")) {

                HijrahDate hijriDate =
                        HijrahDate.from(riyadhDate);

                year = hijriDate.get(ChronoField.YEAR_OF_ERA);

            } else {

                year = riyadhDate.getYear();
            }

            years.add(year);
        }

        return new ArrayList<>(years);
    }
    private BigDecimal calculateGrowthPercentage(
            BigDecimal current,
            BigDecimal previous
    ) {

        if (previous == null) {
            previous = BigDecimal.ZERO;
        }

        if (current == null) {
            current = BigDecimal.ZERO;
        }

        if (previous.compareTo(BigDecimal.ZERO) == 0) {

            if (current.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }

            return BigDecimal.valueOf(100);
        }

        return current
                .subtract(previous)
                .divide(previous, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public List<EmployeeCompletedRequestDto> getEmployeeCompletedRequests(
            Integer employeeId,
            String search
    ) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));

        String keyword = (search == null || search.isBlank())
                ? null
                : search.trim();

        return requestRepository.findCompletedRequestsByEmployee(
                        employee.getId(),
                        StaffRequestStatus.DELIVERED,
                        keyword
                )
                .stream()
                .map(requestMapper::toEmployeeCompletedRequestDto)
                .toList();
    }

    public List<EmployeeAssignedRequestDto> getEmployeeAssignedRequests(
            Integer employeeId
    ) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));

        return requestRepository.findAssignedRequestsByEmployee(
                        employee.getId(),
                        List.of(PricingStatus.values()),
                        StaffRequestStatus.DELIVERED
                )
                .stream()
                .map(requestMapper::toEmployeeAssignedRequestDto)
                .toList();
    }

    
}