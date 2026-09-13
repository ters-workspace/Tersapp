package org.example.tears.Controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.ChatRoomResponse;
import org.example.tears.DTO.SendMessageDto;
import org.example.tears.DTO.UploadResponse;
import org.example.tears.Model.ChatRoom;
import org.example.tears.Model.User;
import org.springframework.http.MediaType;
import org.example.tears.Service.AuthService;
import org.example.tears.Service.ChatService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/chat")
@AllArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    @PostMapping("/send")
    public ApiResponse sendMessage(
            @RequestBody SendMessageDto dto,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        chatService.sendMessage(
                dto,
                user.getPhoneNumber()
        );

        return new ApiResponse(
                true,
                "تم إرسال الرسالة"
        );
    }

    @GetMapping("/{roomId}/room")
    public ApiResponse room(
            @PathVariable Integer roomId,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        ChatRoom room = chatService.getRoom(roomId, user);

        return new ApiResponse(
                true,
                "success",
                new ChatRoomResponse(
                        room.getId(),
                        room.getTicket() != null
                                ? room.getTicket().getId()
                                : null,
                        room.getStatus()
                )
        );
    }


    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse upload(

            @Parameter(description = "File")
            @RequestPart("file")
            MultipartFile file
    ) {

        UploadResponse response = chatService.upload(file);

        return new ApiResponse(
                true,
                "تم رفع الملف",
                response
        );
    }

    @GetMapping("/{roomId}/messages")
    public ApiResponse getMessages(
            @PathVariable Integer roomId,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        return new ApiResponse(
                true,
                "تم جلب الرسائل",
                chatService.getMessages(
                        roomId,
                        user
                )
        );
    }

    @PutMapping("/{roomId}/read")
    public ApiResponse markAsRead(
            @PathVariable Integer roomId,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        chatService.markAsRead(
                roomId,
                user
        );

        return new ApiResponse(
                true,
                "تم تحديث حالة الرسائل"
        );
    }

    @GetMapping("/{roomId}/online")
    public ApiResponse isOnline(
            @PathVariable Integer roomId,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        return new ApiResponse(
                true,
                "success",
                chatService.isOtherUserOnline(
                        roomId,
                        user
                )
        );
    }


    @PostMapping("/direct/{employeeId}")
    public ApiResponse createDirectRoom(
            @PathVariable Integer employeeId,
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        ChatRoom room =
                chatService.createDirectRoom(
                        user,
                        employeeId
                );

        return new ApiResponse(
                true,
                "تم فتح المحادثة",
                new ChatRoomResponse(
                        room.getId(),
                        null,
                        room.getStatus()
                )
        );
    }

    @GetMapping("/my-rooms")
    public ApiResponse getMyRooms(
            HttpServletRequest request
    ) {

        User user = authService.getAuthenticatedUser(request);

        return new ApiResponse(
                true,
                "تم جلب المحادثات",
                chatService.getMyRooms(user)
        );
    }


}