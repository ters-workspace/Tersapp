package org.example.tears.DTO;

import lombok.Data;
import org.example.tears.Enums.JobTitle;

import java.time.LocalDateTime;

@Data
public class EmployeeListDto {

    private Integer id;
    private String fullName;
    private String employeeCode;
    private String email;
    private String phone;
    private String city;
    private JobTitle jobTitle;
    private String status;
    private LocalDateTime joinedAt;
    private Long completedRequests;
}
