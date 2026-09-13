package org.example.tears.Config;

import lombok.AllArgsConstructor;
import org.example.tears.Model.ChatRoom;
import org.example.tears.Model.User;
import org.example.tears.Repository.ChatRoomRepository;
import org.example.tears.Service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;


    @EventListener
    public void handleConnect(SessionConnectedEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        Principal principal = accessor.getUser();

        if (principal instanceof Authentication authentication) {

            User user =
                    (User) authentication.getPrincipal();

            boolean becameOnline =
                    presenceService.online(
                            user.getPhoneNumber()
                    );

            if (becameOnline) {

                broadcastPresence(
                        user,
                        true
                );
            }
        }
    }


    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        Principal principal = accessor.getUser();

        if (principal instanceof Authentication authentication) {

            User user =
                    (User) authentication.getPrincipal();

            boolean becameOffline =
                    presenceService.offline(
                            user.getPhoneNumber()
                    );

            if (becameOffline) {

                broadcastPresence(
                        user,
                        false
                );
            }
        }
    }


    private void broadcastPresence(
            User user,
            boolean online
    ) {

        List<ChatRoom> rooms =
                chatRoomRepository.findAllRoomsForUser(
                        user.getId()
                );

        for (ChatRoom room : rooms) {

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + room.getId(),
                    Map.of(
                            "type", "PRESENCE",
                            "userId", user.getId(),
                            "online", online
                    )
            );
        }
    }
}