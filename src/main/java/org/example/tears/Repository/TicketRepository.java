package org.example.tears.Repository;

import org.example.tears.Enums.TicketStatus;
import org.example.tears.Model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    Optional<Ticket> findById(Integer id);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByCreatedByEmployee_IdOrderByCreatedAtDesc(Integer employeeId);

    Optional<Ticket> findByRequest_IdAndStatusIn(
            Integer requestId,
            List<TicketStatus> statuses
    );

    Optional<Ticket> findByWarrantyRequest_Id(Integer warrantyId);

    List<Ticket> findByRequest_OrderNumberContainingIgnoreCase(String orderNumber);

    long countByStatus(TicketStatus status);

    long countByAssignedSupportEmployee_Id(Integer employeeId);

    long countByAssignedSupportEmployee_IdAndStatus(
            Integer employeeId,
            TicketStatus status
    );
    Optional<Ticket> findByRequest_IdAndCreatedByEmployee_IdAndStatusIn(
            Integer requestId,
            Integer employeeId,
            List<TicketStatus> statuses
    );

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime start,
            LocalDateTime end
    );

    List<Ticket> findByStatusOrderByCreatedAtDesc(
            TicketStatus status
    );
}