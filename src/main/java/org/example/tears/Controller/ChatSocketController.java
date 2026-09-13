package org.example.tears.Controller;


import lombok.AllArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Config.WebSocketSessionRegistry;
import org.example.tears.DTO.DeleteMessageDto;
import org.example.tears.DTO.SendMessageDto;
import org.example.tears.DTO.TypingDto;
import org.example.tears.Model.User;
import org.example.tears.Service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@AllArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;
    private final WebSocketSessionRegistry sessionRegistry;

    @MessageMapping("/chat.send")
    public void send(
            @Payload SendMessageDto dto,
            Principal principal
    ) {
        System.out.println("Principal class = " + principal.getClass());
        System.out.println("Principal = " + principal);

        Authentication authentication = (Authentication) principal;

        System.out.println("Authentication Principal = " + authentication.getPrincipal());

        User user = (User) authentication.getPrincipal();

        System.out.println("Phone = " + user.getPhoneNumber());

        chatService.sendMessage(dto, user.getPhoneNumber());
    }

    @MessageMapping("/chat.typing")
    public void typing(
            @Payload TypingDto dto,
            StompHeaderAccessor accessor
    ) {

        String sessionId = accessor.getSessionId();

        User user = sessionRegistry.getUser(sessionId);

        if (user == null) {
            throw new ApiException("المستخدم غير موجود");
        }

        chatService.sendTyping(
                dto,
                user.getPhoneNumber()
        );
    }

    @MessageMapping("/chat.delete")
    public void delete(
            @Payload DeleteMessageDto dto,
            Principal principal
    ) {
        chatService.deleteMessage(dto, principal.getName());
    }



}