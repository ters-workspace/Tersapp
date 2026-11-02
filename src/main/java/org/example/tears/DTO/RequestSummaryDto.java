package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RequestSummaryDto {
        private Integer id;
        private String orderNumber;
        private String status;
        private String stage;

        private String customerName;
        private String customerPhone;

        private String assignedTechnician;
        private String serviceName;
        private String city;
        private LocalDateTime createdAt; // 👈 مهم

    }