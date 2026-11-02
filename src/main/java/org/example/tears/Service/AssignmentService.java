package org.example.tears.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.tears.Enums.*;
import org.example.tears.Mapper.RequestMapper;
import org.example.tears.Model.CarServiceRequest;
import org.example.tears.Model.Employee;
import org.example.tears.Repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final CarServiceRequestRepository requestRepo;
    private final NotificationService notificationService;
    private final SocketService socketService;
    private final CarServiceRequestService carServiceRequestService;
    private final RequestQueryService requestQueryService;
    private final RequestMapper requestMapper;


    private final EmployeeRepository employeeRepo;

    @Transactional
    public void assign(Integer requestId, Integer employeeId) {

        CarServiceRequest request = requestRepo.findById(requestId)
                .orElseThrow();

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow();

        // 🔥 1. ربط الموظف
        request.setAssignedTechnician(employee);

        // 🔥 2. تغيير الحالة (مهم جدًا)
        request.setStaffStatus(StaffRequestStatus.NEW);

        request.setStage(WorkflowStage.ASSIGNED);

        request.setLastUpdated(LocalDateTime.now());

        requestRepo.save(request);

        socketService.send(
                "/topic/current-orders/" +
                        request.getCustomer().getUser().getId(),
                carServiceRequestService.toCurrentDto(request)
        );
        Employee assignedTechnician =
                request.getAssignedTechnician();

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
            socketService.send(
                    "/topic/employee-request-details/" +
                            request.getId(),

                    requestMapper.toEmployeeDetailsDto(request)
            );
        }

        socketService.send(
                "/topic/employee-request-count/" +
                        employee.getUser().getId(),
                requestQueryService.getMyNewRequestsCount(employee)
        );

        notificationService.send(
                assignedTechnician.getUser(),
                NotificationType.REQUEST_ASSIGNED,
                NotificationCategory.REQUEST,
                "تم إسناد طلب جديد لك",
                "تم إسناد طلب جديد لك، يمكنك الآن متابعة الطلب.",
                NotificationActionType.OPEN_ENTITY,
                NotificationEntityType.REQUEST,
                request.getId().toString(),
                NotificationSection.REQUESTS
        );
    }

}
