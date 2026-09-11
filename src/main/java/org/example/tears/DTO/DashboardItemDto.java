package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardItemDto {

    private String itemType;

    private Integer requestId;
    private Integer ticketId;
    private Integer warrantyId;

    private String referenceNumber;

    private String customerName;
    private String customerPhone;

    private String serviceType;

    private Double totalAmount;

    private LocalDateTime createdAt;

    private Integer responsibleEmployeeId;
    private String responsibleEmployeeName;
    private String responsibleEmployeeEmail;

    private String status;

    private boolean canAssign;
}