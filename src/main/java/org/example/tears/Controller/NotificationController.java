package org.example.tears.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.NotificationListResponse;
import org.example.tears.DTO.RegisterDeviceDto;
import org.example.tears.DTO.UnregisterDeviceDto;
import org.example.tears.Enums.UserRole;
import org.example.tears.Model.User;
import org.example.tears.DTO.RequestNotificationSummaryDto;
import org.example.tears.Service.AuthService;
import org.example.tears.Service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tears/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;


    // إشعاراتي
    @GetMapping("/my-notification")
    public NotificationListResponse myNotifications(
            @AuthenticationPrincipal User user
    ) {
        return notificationService
                .getUserNotifications(user.getId());
    }


    // قراءة إشعار
    @PutMapping("/notification/{id}/read")
    public ApiResponse markRead(
            @PathVariable Integer id,
            @AuthenticationPrincipal User user
    ) {

        notificationService.markAsRead(
                id,
                user.getId()
        );

        return new ApiResponse(true, "تم تحديث الإشعار");
    }

    @PutMapping("/read-all")
    public ApiResponse markAllAsRead(
            @AuthenticationPrincipal User user
    ) {

        notificationService.markAllAsRead(user.getId());

        return new ApiResponse(
                true,
                "تم تحديث جميع الإشعارات"
        );
    }

    @PostMapping("/device")
    public ApiResponse registerDevice(
            @AuthenticationPrincipal User user,
            @RequestBody RegisterDeviceDto dto
    ) {
        String fcmToken = notificationService.registerDevice(user, dto);

        return new ApiResponse(
                true,
                "تم تسجيل الجهاز",
                fcmToken
        );
    }

    @DeleteMapping("/notifications/device")
    public ResponseEntity<ApiResponse> unregisterDevice(
            HttpServletRequest request,
            @RequestBody UnregisterDeviceDto dto
    ) {

        User user = authService.getAuthenticatedUser(request);

        notificationService.unregisterDevice(
                user,
                dto.getFcmToken()
        );

        return ResponseEntity.ok(
                new ApiResponse(
                        true,
                        "Device unregistered successfully",
                        null
                )
        );
    }

    @GetMapping("/admin/devices")
    public ApiResponse getAllDevicesForAdmin(
            @AuthenticationPrincipal User user
    ) {

        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ApiException("Access denied");
        }

        return new ApiResponse(
                true,
                "تم جلب الأجهزة المسجلة",
                notificationService.getAllDevicesForAdmin()
        );
    }
    @GetMapping("/notification/{id}/request-summary")
    public RequestNotificationSummaryDto getRequestSummary(
            @PathVariable Integer id,
            @AuthenticationPrincipal User user
    ) {
        return notificationService.getRequestSummaryForNotification(
                id,
                user
        );
    }

}
