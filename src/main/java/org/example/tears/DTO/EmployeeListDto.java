package org.example.tears.DTO;

import lombok.Data;
import org.example.tears.Enums.JobTitle;

@Data
public class EmployeeListDto {

    private Integer id;

    private String fullName;

    private String phoneNumber;

    private JobTitle jobTitle;

    private String role;

    private String status;
}