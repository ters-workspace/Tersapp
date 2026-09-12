package org.example.tears.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.*;
import org.example.tears.Enums.DashboardSection;
import org.example.tears.InpDTO.AdminCreateEmployeeDTO;
import org.example.tears.Model.Appointment;
import org.example.tears.Model.Employee;
import org.example.tears.OutDTO.EmployeeLoginInfo;
import org.example.tears.Service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
    @RequestMapping("api/v1/tears/dashboard")
    @RequiredArgsConstructor
    @PreAuthorize("hasRole('ADMIN')")

public class AdminController {

    private final AdminService adminService;
    private final AssignmentService assignmentService;
    private final RequestQueryService requestQueryService;
    private final AuthService authService;
    private final DashboardService dashboardService;
    private final ChatService chatService;

    @GetMapping("/get")
    public ApiResponse getDashboard() {

        return new ApiResponse(
                true,
                "تم جلب إحصائيات لوحة التحكم",
                dashboardService.getDashboard()
        );
    }

    @PutMapping("/admin/reset-password")
    public ApiResponse resetAdminPassword(
            @RequestBody ResetAdminPasswordDto dto
    ) {
        authService.resetAdminPassword(dto);

        return new ApiResponse(
                true,
                "تم تغيير كلمة مرور الأدمن"
        );
    }

    @GetMapping("/all/requests")
    public List<RequestSummaryDto> getAll() {
        return requestQueryService.getAllRequests();
    }

    @GetMapping("/requests/unassigned")
    public List<RequestSummaryDto> unassigned() {
        return requestQueryService.getUnassigned();
    }

    @PostMapping("/assign")
    public ApiResponse assign(@RequestParam Integer requestId,
                              @RequestParam Integer employeeId) {
        assignmentService.assign(requestId, employeeId);
        return new ApiResponse(true, "تم الإسناد");
    }

    @GetMapping("/all/employees")
    public List<EmployeeListDto> employees() {
        return adminService.getAllEmployees();
    }

    @GetMapping("/employees")
    public ResponseEntity<EmployeeDashboardDto> getEmployees(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(
                dashboardService.getEmployees(search)
        );
    }

    @GetMapping("/requests/search")
    public List<RequestSummaryDto> search(
            @RequestParam(required = false)
            String search
    ) {
        return requestQueryService.search(search);
    }


    @GetMapping("/stats")
    public ApiResponse getDashboardStats(
            @RequestParam(
                    defaultValue = "ALL"
            )
            DashboardSection section
    ) {

        return new ApiResponse(
                true,
                "تم جلب إحصائيات الطلبات",
                dashboardService.getStats(section)
        );
    }

    @GetMapping("/items")
    public ApiResponse getDashboardItems(

            @RequestParam(
                    defaultValue = "ALL"
            )
            DashboardSection section,

            @RequestParam(
                    required = false
            )
            String status
    ) {

        return new ApiResponse(
                true,
                "تم جلب بيانات لوحة التحكم",
                dashboardService.getItems(
                        section,
                        status
                )
        );
    }

    @GetMapping("/contact")
    public ResponseEntity<DashboardContactResponseDto> getContactInfo(
            @RequestParam String itemType,
            @RequestParam Integer itemId
    ) {
        return ResponseEntity.ok(
                dashboardService.getContactInfo(itemType, itemId)
        );
    }

    @PostMapping("/admin/assign")
    public ApiResponse assignRequest(
            @RequestParam Integer requestId,
            @RequestParam Integer employeeId
    ) {

        assignmentService.assign(requestId, employeeId);

        return new ApiResponse(
                true,
                "تم إسناد الطلب بنجاح"
        );
    }

    @PostMapping("/admin/add/employee")
    public ResponseEntity<EmployeeLoginInfo> createEmployee(
            @Valid @RequestBody AdminCreateEmployeeDTO dto
    ) {

        return ResponseEntity.ok(
                adminService.createEmployee(dto)
        );
    }

    @PutMapping("/admin/employees/{id}/deactivate")
        public ApiResponse deactivate(@PathVariable Integer id) {
            return adminService.deactivateEmployee(id);
        }


    @PostMapping("/admin/migrate-legacy-rooms")
    public ApiResponse migrateLegacyRooms() {

        chatService.migrateLegacyChatRooms();

        return new ApiResponse(
                true,
                "تم تحديث غرف المحادثات القديمة"
        );
    }
}
