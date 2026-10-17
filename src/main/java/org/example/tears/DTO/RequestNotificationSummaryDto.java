package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestNotificationSummaryDto {

    private String orderNumber;

    private String customerName;

    private Double orderValue;

    private String requestSource;

    private LocalDate appointmentDate;

    private LocalTime appointmentTime;

    private String assignedTechnicianName;
}