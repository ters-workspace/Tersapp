package org.example.tears.Repository;

import org.example.tears.Enums.ReadStatus;
import org.example.tears.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.example.tears.Model.ChatMessage;
import org.example.tears.Model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    List<ChatMessage> findByChatRoomOrderByCreatedAtAsc(ChatRoom chatRoom);

    Page<ChatMessage> findByChatRoomOrderByCreatedAtDesc(
            ChatRoom chatRoom,
            Pageable pageable
    );

    ChatMessage findTopByChatRoomOrderByCreatedAtDesc(
            ChatRoom chatRoom
    );

    long countByChatRoomAndSenderNotAndReadStatus(
            ChatRoom chatRoom,
            User sender,
            ReadStatus readStatus
    );

}
