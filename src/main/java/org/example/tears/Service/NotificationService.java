package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Model.*;
import org.example.tears.Repository.NotificationRepository;
import org.example.tears.Enums.NotificationActionType;
import org.example.tears.Enums.NotificationCategory;
import org.example.tears.Enums.NotificationEntityType;
import org.example.tears.Enums.NotificationSection;
import org.example.tears.Enums.NotificationType;
import org.example.tears.Repository.UserDeviceRepository;
import org.example.tears.Enums.UserRole;
import org.example.tears.Enums.UserStatus;
import org.example.tears.Repository.UserRepository;
import org.example.tears.DTO.RequestNotificationSummaryDto;
import org.example.tears.Repository.CarServiceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final SocketService socketService;
    private final FcmService fcmService;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final CarServiceRequestRepository carServiceRequestRepository;

    public void send(
            User user,
            NotificationType type,
            NotificationCategory category,
            String title,
            String body,
            NotificationActionType actionType,
            NotificationEntityType entityType,
            String entityId,
            NotificationSection section
    ) {

        if (user == null) {
            return;
        }

        Notification n = new Notification();

        n.setUser(user);

        n.setType(type);
        n.setCategory(category);
        n.setTitle(title);
        n.setBody(body);

        n.setActionType(actionType);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setSection(section);

        n.setReadStatus(false);
        n.setReadAt(null);
        n.setCreatedAt(LocalDateTime.now());

        Notification saved = repo.save(n);

        log.info(
                "Notification saved: userId={}, notificationId={}",
                user.getId(),
                saved.getId()
        );

// WebSocket + FCM
        if (Boolean.TRUE.equals(user.getNotificationsEnabled())) {

            socketService.send(
                    "/topic/notifications/" + user.getId(),
                    toDto(saved)
            );

            log.info(
                    "Sending FCM notification: userId={}, notificationId={}",
                    user.getId(),
                    saved.getId()
            );

            fcmService.send(user, saved);
        }
    }


    public void notifyActiveAdminsAboutNewRequest(
            CarServiceRequest request
    ) {
        List<User> admins = userRepository.findByRoleAndStatus(
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );

        String orderNumber = request.getOrderNumber();
        String amount = String.valueOf(request.getEstimatedPrice());

        for (User admin : admins) {
            send(
                    admin,
                    NotificationType.REQUEST_CREATED,
                    NotificationCategory.REQUEST,
                    "طلب جديد بانتظار الإسناد",
                    "تم إنشاء طلب جديد رقم "
                            + orderNumber
                            + " بقيمة "
                            + amount
                            + " ر.س، يرجى مراجعة تفاصيل الطلب.",
                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.REQUEST,
                    request.getId().toString(),
                    NotificationSection.REQUESTS
            );
        }
    }
    public void notifyActiveAdminsAboutNewTicket(Ticket ticket) {

        if (ticket == null || ticket.getId() == null) {
            return;
        }

        List<User> admins = userRepository.findByRoleAndStatus(
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );

        String ticketNumber = ticket.getTicketNumber() != null
                ? ticket.getTicketNumber()
                : "TK-" + ticket.getId();

        String orderNumber = ticket.getRequest() != null
                && ticket.getRequest().getOrderNumber() != null
                ? ticket.getRequest().getOrderNumber()
                : "غير متوفر";

        String customerName = ticket.getCustomer() != null
                && ticket.getCustomer().getUser() != null
                && ticket.getCustomer().getUser().getFullName() != null
                ? ticket.getCustomer().getUser().getFullName()
                : "غير معروف";

        String problemType = ticket.getProblemType() != null
                ? ticket.getProblemType().name()
                : "غير محدد";

        String priority = ticket.getPriority() != null
                ? ticket.getPriority().name()
                : "غير محددة";

        String body = "تم إنشاء تذكرة جديدة رقم "
                + ticketNumber
                + " للطلب رقم "
                + orderNumber
                + ". العميل: "
                + customerName
                + ". نوع المشكلة: "
                + problemType
                + ". الأولوية: "
                + priority
                + ".";

        for (User admin : admins) {
            send(
                    admin,
                    NotificationType.SUPPORT_TICKET_CREATED,
                    NotificationCategory.SUPPORT,
                    "تذكرة جديدة",
                    body,
                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.SUPPORT_TICKET,
                    ticket.getId().toString(),
                    NotificationSection.SUPPORT
            );
        }
    }

    public void notifyActiveAdminsAboutNewWarranty(
            WarrantyRequest warranty
    ) {
        if (warranty == null || warranty.getId() == null) {
            return;
        }

        List<User> admins = userRepository.findByRoleAndStatus(
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );

        String warrantyId = warranty.getId().toString();

        String orderNumber = warranty.getRequest() != null
                && warranty.getRequest().getOrderNumber() != null
                ? warranty.getRequest().getOrderNumber()
                : "غير متوفر";

        String customerName = warranty.getCustomer() != null
                && warranty.getCustomer().getUser() != null
                && warranty.getCustomer().getUser().getFullName() != null
                ? warranty.getCustomer().getUser().getFullName()
                : "غير معروف";

        String body = "تم إنشاء طلب ضمان جديد رقم #"
                + warrantyId
                + " للطلب الأساسي رقم "
                + orderNumber
                + ". العميل: "
                + customerName
                + ".";

        for (User admin : admins) {
            send(
                    admin,
                    NotificationType.WARRANTY_CREATED,
                    NotificationCategory.WARRANTY,
                    "طلب ضمان جديد",
                    body,
                    NotificationActionType.OPEN_ENTITY,
                    NotificationEntityType.WARRANTY,
                    warrantyId,
                    NotificationSection.WARRANTY
            );
        }
    }

    private NotificationDto toDto(Notification notification) {

        NotificationActionDto action = new NotificationActionDto();

        action.setType(notification.getActionType());
        action.setEntityType(notification.getEntityType());
        action.setEntityId(notification.getEntityId());
        action.setSection(notification.getSection());

        NotificationDto dto = new NotificationDto();

        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setCategory(notification.getCategory());
        dto.setTitle(notification.getTitle());
        dto.setBody(notification.getBody());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setReadStatus(notification.isReadStatus());
        dto.setReadAt(notification.getReadAt());
        dto.setAction(action);

        return dto;
    }

    public NotificationListResponse getUserNotifications(
            Integer userId
    ) {

        List<Notification> notifications =
                repo.findByUserIdOrderByCreatedAtDesc(userId);

        List<NotificationDto> items =
                notifications.stream()
                        .map(this::toDto)
                        .toList();

        long unreadCount =
                notifications.stream()
                        .filter(notification ->
                                !notification.isReadStatus())
                        .count();

        return new NotificationListResponse(
                items,
                unreadCount,
                false,
                null
        );
    }

    public void markAsRead(
            Integer notificationId,
            Integer userId
    ) {

        Notification notification =
                repo.findByIdAndUserId(
                        notificationId,
                        userId
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Notification not found"
                        )
                );

        notification.setReadStatus(true);
        notification.setReadAt(LocalDateTime.now());

        repo.save(notification);
    }

    @Transactional
    public void markAllAsRead(Integer userId) {

        List<Notification> notifications =
                repo.findByUserIdOrderByCreatedAtDesc(userId);

        LocalDateTime now = LocalDateTime.now();

        notifications.stream()
                .filter(notification -> !notification.isReadStatus())
                .forEach(notification -> {
                    notification.setReadStatus(true);
                    notification.setReadAt(now);
                });

        repo.saveAll(notifications);
    }

    public String registerDevice(User user, RegisterDeviceDto dto) {

        UserDevice device =
                userDeviceRepository
                        .findByFcmToken(dto.getFcmToken())
                        .orElse(new UserDevice());

        device.setUser(user);
        device.setFcmToken(dto.getFcmToken());
        device.setDeviceType(dto.getDeviceType());
        device.setActive(true);
        device.setLastSeen(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());

        if (device.getCreatedAt() == null) {
            device.setCreatedAt(LocalDateTime.now());
        }

        userDeviceRepository.save(device);

        return device.getFcmToken();
    }

    @Transactional
    public void unregisterDevice(User user, String fcmToken) {

        if (fcmToken == null || fcmToken.isBlank()) {
            throw new ApiException("FCM token is required");
        }

        UserDevice device =
                userDeviceRepository
                        .findByFcmTokenAndUserId(
                                fcmToken,
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new ApiException("Device not found"));

        device.setActive(false);
        device.setUpdatedAt(LocalDateTime.now());

        userDeviceRepository.save(device);
    }

    public List<AdminDeviceDto> getAllDevicesForAdmin() {

        return userDeviceRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(device -> new AdminDeviceDto(
                        device.getId(),
                        device.getUser().getId(),
                        device.getUser().getFullName(),
                        device.getUser().getPhoneNumber(),
                        device.getFcmToken(),
                        device.getDeviceType(),
                        device.getActive(),
                        device.getLastSeen()
                ))
                .toList();
    }
    public RequestNotificationSummaryDto getRequestSummaryForNotification(
            Integer notificationId,
            User user
    ) {
        // Only admins can access the request summary.
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ApiException("Access denied");
        }

        // Make sure this notification belongs to the authenticated admin.
        Notification notification = repo
                .findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() ->
                        new ApiException("Notification not found")
                );

        // This endpoint supports request notifications only.
        if (notification.getEntityType() != NotificationEntityType.REQUEST
                || notification.getEntityId() == null) {
            throw new ApiException(
                    "This notification is not linked to a request"
            );
        }

        Integer requestId;

        try {
            requestId = Integer.valueOf(notification.getEntityId());
        } catch (NumberFormatException exception) {
            throw new ApiException("Invalid request reference");
        }

        // Load the request linked to the notification.
        CarServiceRequest request = carServiceRequestRepository
                .findById(requestId)
                .orElseThrow(() ->
                        new ApiException("Request not found")
                );

        // Customer name.
        String customerName = null;

        if (request.getCustomer() != null
                && request.getCustomer().getUser() != null) {
            customerName = request.getCustomer()
                    .getUser()
                    .getFullName();
        }

        // Assigned technician name, not the current or pricing employee.
        String assignedTechnicianName = null;

        if (request.getAssignedTechnician() != null
                && request.getAssignedTechnician().getUser() != null) {
            assignedTechnicianName = request.getAssignedTechnician()
                    .getUser()
                    .getFullName();
        }

        return new RequestNotificationSummaryDto(
                request.getOrderNumber(),
                customerName,
                request.getEstimatedPrice(),
                "تطبيق العميل",
                request.getAppointmentDate(),
                request.getAppointmentTime(),
                assignedTechnicianName
        );
    }
}