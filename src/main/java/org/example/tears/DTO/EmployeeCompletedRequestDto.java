package org.example.tears.DTO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EmployeeCompletedRequestDto {

    private Integer id;
    private String orderNumber;
    private String serviceName;
    private String customerName;
    private String city;
    private LocalDateTime completedAt;
    private String status;
}