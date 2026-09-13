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
import org.springframework.web.socket.messaging.SessionConnectEvent;
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
    private final WebSocketSessionRegistry sessionRegistry;


    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();

        User user = sessionRegistry.getUser(sessionId);

        if (user != null) {

            boolean becameOffline =
                    presenceService.offline(user.getPhoneNumber());

            if (becameOffline) {
                broadcastPresence(user, false);
            }

            sessionRegistry.remove(sessionId);
        }
    }


    private void broadcastPresence(
            User user,
            boolean online
    ) {

        System.out.println(
                "========== BROADCAST PRESENCE =========="
        );

        System.out.println(
                "USER ID = "
                        + user.getId()
                        + " | ONLINE = "
                        + online
        );

        List<ChatRoom> rooms =
                chatRoomRepository.findAllRoomsForUser(
                        user.getId()
                );

        System.out.println(
                "ROOMS FOUND = " + rooms.size()
        );

        for (ChatRoom room : rooms) {

            System.out.println(
                    "PUBLISH PRESENCE -> /topic/chat/"
                            + room.getId()
            );

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