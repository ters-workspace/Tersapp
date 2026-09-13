package org.example.tears.Controller;


import lombok.AllArgsConstructor;
import org.example.tears.DTO.DeleteMessageDto;
import org.example.tears.DTO.SendMessageDto;
import org.example.tears.DTO.TypingDto;
import org.example.tears.Model.User;
import org.example.tears.Service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@AllArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;

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
    public void typing(@Payload TypingDto dto, Principal principal) {

        System.out.println("========== TYPING RECEIVED ==========");
        System.out.println("ROOM ID = " + dto.getRoomId());
        System.out.println("TYPING = " + dto.getTyping());
        System.out.println("PRINCIPAL = " + principal);

        chatService.sendTyping(dto, principal.getName());

        System.out.println("========== TYPING HANDLED ==========");
    }

    @MessageMapping("/chat.delete")
    public void delete(
            @Payload DeleteMessageDto dto,
            Principal principal
    ) {
        chatService.deleteMessage(dto, principal.getName());
    }



}