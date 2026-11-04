package org.example.tears.DTO;

import lombok.Data;

@Data
public class EmployeeAssignedRequestDto {

    private Integer id;
    private String orderNumber;
    private String serviceName;
    private String customerName;
    private String customerPhone;
}