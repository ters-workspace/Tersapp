package org.example.tears.Service;

import lombok.AllArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.*;
import org.example.tears.Enums.ChatStatus;
import org.example.tears.Enums.MessageType;
import org.example.tears.Enums.ReadStatus;
import org.example.tears.Enums.UserRole;
import org.example.tears.Model.*;
import org.example.tears.Repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.example.tears.Enums.ReadStatus.SENT;


@Service
@AllArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TicketRepository ticketRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileStorageService fileStorageService;

    private final UserRepository userRepo;
    private final PresenceService presenceService;
    private final EmployeeRepository employeeRepository;


    @Transactional
    public ChatRoom createRoomIfNotExists(
            Ticket ticket
    ) {

        return chatRoomRepository
                .findByTicket(ticket)
                .orElseGet(() -> {

                    ChatRoom room = new ChatRoom();

                    room.setTicket(ticket);

                    room.setCreatedAt(LocalDateTime.now());

                    room.setStatus(ChatStatus.OPEN);

                    return chatRoomRepository.save(room);
                });
    }

    public ChatRoom getRoom(Integer roomId, User user) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new ApiException("المحادثة غير موجودة"));

        if (room.getTicket() != null) {
            validateUserAccess(room.getTicket(), user);
        } else {
            validateDirectRoomAccess(room, user);
        }

        return room;
    }

    public List<ChatMessageResponse> getMessages(Integer roomId, User user) {

        ChatRoom room = getRoom(roomId, user);

        return chatMessageRepository
                .findByChatRoomOrderByCreatedAtAsc(room)
                .stream()
                .map(message -> mapToResponse(message, user))
                .toList();
    }

    private void validateDirectRoomAccess(
            ChatRoom room,
            User user
    ) {

        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        if (room.getUserOne() != null &&
                room.getUserOne().getId().equals(user.getId())) {
            return;
        }

        if (room.getUserTwo() != null &&
                room.getUserTwo().getId().equals(user.getId())) {
            return;
        }

        throw new ApiException("غير مصرح لك بالدخول لهذه المحادثة");
    }


    public Page<ChatMessage> getMessagesPage(
            Integer roomId,
            User user,
            Pageable pageable
    ) {

        ChatRoom room = getRoom(roomId, user);

        return chatMessageRepository
                .findByChatRoomOrderByCreatedAtDesc(
                        room,
                        pageable
                );
    }

    public void validateUserAccess(
            Ticket ticket,
            User user
    ) {

        // الأدمن
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        // العميل (إذا مستقبلاً صار في محادثة عميل)
        if (user.getCustomer() != null &&
                ticket.getCustomer() != null &&
                ticket.getCustomer().getId().equals(user.getCustomer().getId())) {
            return;
        }

        // الموظف
        if (user.getEmployee() != null) {

            Integer employeeId = user.getEmployee().getId();

            // موظف الصيانة (منشئ التذكرة)
            if (ticket.getCreatedByEmployee() != null &&
                    ticket.getCreatedByEmployee().getId().equals(employeeId)) {
                return;
            }

            // موظف خدمة العملاء
            if (ticket.getAssignedSupportEmployee() != null &&
                    ticket.getAssignedSupportEmployee().getId().equals(employeeId)) {
                return;
            }
        }

        throw new ApiException("غير مصرح لك بالدخول لهذه المحادثة");
    }

    @Transactional
    public void sendMessage(
            SendMessageDto dto,
            String phone
    ) {

        User sender = userRepo
                .findByPhoneNumber(phone)
                .orElseThrow(() ->
                        new ApiException("المستخدم غير موجود"));

        ChatRoom room = getRoom(
                dto.getRoomId(),
                sender
        );

        ChatMessage message = new ChatMessage();

        message.setChatRoom(room);
        message.setSender(sender);
        message.setType(dto.getType());
        message.setMessage(dto.getMessage());
        message.setFileUrl(dto.getFileUrl());
        message.setFileName(dto.getFileName());
        message.setFileSize(dto.getFileSize());
        message.setReadStatus(ReadStatus.SENT);
        message.setVoiceDuration(dto.getVoiceDuration());
        message.setCreatedAt(LocalDateTime.now());

        chatMessageRepository.save(message);

        ChatMessageResponse response =
                mapToResponse(message, sender);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId(),
                response
        );
    }

    private ChatMessageResponse mapToResponse(
            ChatMessage message,
            User currentUser
    ) {

        ChatMessageResponse dto = new ChatMessageResponse();

        dto.setId(message.getId());

        dto.setSenderId(message.getSender().getId());
        dto.setSenderName(message.getSender().getFullName());

        dto.setType(message.getType());
        dto.setMessage(message.getMessage());

        dto.setStatus(message.getReadStatus());

        dto.setMine(
                currentUser != null &&
                        message.getSender() != null &&
                        message.getSender().getId().equals(currentUser.getId())
        );

        dto.setFileUrl(message.getFileUrl());
        dto.setFileName(message.getFileName());
        dto.setFileSize(message.getFileSize());
        dto.setVoiceDuration(message.getVoiceDuration());

        dto.setCreatedAt(message.getCreatedAt());

        return dto;
    }

    public void markAsRead(
            Integer roomId,
            User currentUser
    ) {

        ChatRoom room = getRoom(roomId, currentUser);

        List<ChatMessage> messages =
                chatMessageRepository
                        .findByChatRoomOrderByCreatedAtAsc(room);

        for (ChatMessage message : messages) {

            if (message.getSender().getId()
                    .equals(currentUser.getId())) {
                continue;
            }

            if (message.getReadStatus() == ReadStatus.READ) {
                continue;
            }

            message.setReadStatus(ReadStatus.READ);

            chatMessageRepository.save(message);

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + room.getId() + "/read",
                    message.getId()
            );
        }
    }


    public Boolean isOtherUserOnline(
            Integer roomId,
            User currentUser
    ) {

        ChatRoom room = getRoom(roomId, currentUser);

        // الغرف القديمة قد لا تحتوي على userOne / userTwo
        if (room.getUserOne() == null || room.getUserTwo() == null) {
            return false;
        }

        User otherUser;

        if (room.getUserOne().getId().equals(currentUser.getId())) {
            otherUser = room.getUserTwo();
        } else {
            otherUser = room.getUserOne();
        }

        return presenceService.isOnline(
                otherUser.getPhoneNumber()
        );
    }

    public UploadResponse upload(MultipartFile file) {

        String fileUrl =
                fileStorageService.saveFile(file, "chat");

        return new UploadResponse(
                fileUrl,
                file.getOriginalFilename(),
                file.getSize()
        );
    }

    @Transactional
    public void sendSystemMessage(ChatRoom room, String text) {

        User admin = userRepo.findById(8)
                .orElseThrow(() -> new ApiException("Admin غير موجود"));

        ChatMessage message = new ChatMessage();
        message.setChatRoom(room);
        message.setSender(admin);
        message.setType(MessageType.SYSTEM);
        message.setMessage(text);
        message.setCreatedAt(LocalDateTime.now());
        message.setReadStatus(SENT);

        chatMessageRepository.save(message);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId(),
                mapToResponse(message, admin)
        );
    }

    public void sendTyping(
            TypingDto dto,
            String phone
    ) {

        User sender = userRepo.findByPhoneNumber(phone)
                .orElseThrow(() ->
                        new ApiException("المستخدم غير موجود"));

        ChatRoom room = getRoom(
                dto.getRoomId(),
                sender
        );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId() + "/typing",
                Map.of(
                        "userId", sender.getId(),
                        "userName", sender.getFullName(),
                        "typing", dto.getTyping()
                )
        );
    }

    @Transactional
    public void deleteMessage(
            DeleteMessageDto dto,
            String phone
    ) {

        User user = userRepo.findByPhoneNumber(phone)
                .orElseThrow(() -> new ApiException("المستخدم غير موجود"));

        ChatMessage message = chatMessageRepository.findById(dto.getMessageId())
                .orElseThrow(() -> new ApiException("الرسالة غير موجودة"));

        // فقط صاحب الرسالة أو الأدمن
        if (!message.getSender().getId().equals(user.getId())
                && user.getRole() != UserRole.ADMIN) {
            throw new ApiException("غير مصرح لك بحذف الرسالة");
        }

        message.setDeleted(true);

        chatMessageRepository.save(message);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + message.getChatRoom().getId() + "/delete",
                Map.of(
                        "messageId", message.getId()
                )
        );
    }

    @Transactional
    public ChatRoom createDirectRoom(
            User currentUser,
            Integer employeeId
    ) {

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new ApiException("الموظف غير موجود"));

        User otherUser = employee.getUser();

        if (currentUser.getId().equals(otherUser.getId())) {
            throw new ApiException("لا يمكنك مراسلة نفسك");
        }

        return chatRoomRepository
                .findDirectRoom(
                        currentUser.getId(),
                        otherUser.getId()
                )
                .orElseGet(() -> {

                    ChatRoom room = new ChatRoom();

                    room.setUserOne(currentUser);
                    room.setUserTwo(otherUser);
                    room.setStatus(ChatStatus.OPEN);
                    room.setReadStatus(ReadStatus.SENT);

                    return chatRoomRepository.save(room);
                });
    }


    @Transactional
    public void migrateLegacyChatRooms() {

        List<ChatRoom> rooms = chatRoomRepository.findLegacyTicketRooms();

        for (ChatRoom room : rooms) {

            Ticket ticket = room.getTicket();

            if (ticket == null) {
                continue;
            }

            if (ticket.getCustomer() == null
                    || ticket.getCustomer().getUser() == null) {
                continue;
            }

            if (ticket.getAssignedSupportEmployee() == null
                    || ticket.getAssignedSupportEmployee().getUser() == null) {
                continue;
            }

            room.setUserOne(ticket.getCustomer().getUser());
            room.setUserTwo(
                    ticket.getAssignedSupportEmployee().getUser()
            );
        }
    }

}
