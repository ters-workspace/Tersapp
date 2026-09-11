package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.*;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CarServiceRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final WarrantyRepository warrantyRepository;


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

    public DashboardDto getDashboard() {

        // هنا نحط الـ counts الحقيقية من الـ repositories

        return new DashboardDto(
                // totalRequests
                // totalEmployees
                // totalRevenue
                // customerApp
                // employeeApp
                // customerService
        );
    }
}