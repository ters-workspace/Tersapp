package org.example.tears.Repository;

import org.example.tears.Model.ChatRoom;
import org.example.tears.Model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Integer> {

    Optional<ChatRoom> findByTicket(Ticket ticket);

    @Query("""
    SELECT c
    FROM ChatRoom c
    WHERE c.ticket IS NULL
    AND (
        (c.userOne.id = :user1Id AND c.userTwo.id = :user2Id)
        OR
        (c.userOne.id = :user2Id AND c.userTwo.id = :user1Id)
    )
""")
    Optional<ChatRoom> findDirectRoom(
            Integer user1Id,
            Integer user2Id
    );

    @Query("""
    SELECT c
    FROM ChatRoom c
    WHERE c.ticket IS NOT NULL
    AND c.userOne IS NULL
    AND c.userTwo IS NULL
""")
    List<ChatRoom> findLegacyTicketRooms();

    @Query("""
    SELECT c
    FROM ChatRoom c
    LEFT JOIN c.ticket t
    LEFT JOIN t.customer customer
    LEFT JOIN customer.user customerUser
    LEFT JOIN t.createdByEmployee createdBy
    LEFT JOIN createdBy.user createdByUser
    LEFT JOIN t.assignedSupportEmployee support
    LEFT JOIN support.user supportUser
    WHERE
        c.userOne.id = :userId
        OR c.userTwo.id = :userId
        OR customerUser.id = :userId
        OR createdByUser.id = :userId
        OR supportUser.id = :userId
""")
    List<ChatRoom> findAllRoomsForUser(Integer userId);

    @Query("""
    SELECT c
    FROM ChatRoom c
    WHERE c.userOne.id = :userId
    OR c.userTwo.id = :userId
""")
    List<ChatRoom> findAllByUserId(Integer userId);
}