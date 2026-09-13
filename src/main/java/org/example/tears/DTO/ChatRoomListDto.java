package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatRoomListDto {

    private Integer roomId;

    private Integer otherUserId;
    private String otherUserName;

    private Integer ticketId;

    private String lastMessage;
    private String lastMessageType;
    private LocalDateTime lastMessageTime;

    private long unreadCount;
}