package org.example.tears.DTO;

import lombok.Data;
import org.example.tears.Enums.TicketPriority;
import org.example.tears.Enums.TicketProblemType;
import org.example.tears.Enums.TicketStatus;

import java.time.LocalDateTime;

@Data
public class TicketListDto {

    private Integer id;

    private String ticketNumber;

    private String orderNumber;

    private Integer requestId;

    private String customerName;

    private TicketProblemType problemType;

    private String description;

    private TicketPriority priority;

    private TicketStatus status;

    private Boolean acceptedByCustomerService;

    private String createdByEmployeeName;

    private LocalDateTime createdAt;
}