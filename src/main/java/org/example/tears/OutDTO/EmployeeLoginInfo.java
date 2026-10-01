package org.example.tears.OutDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.EmployeeCity;
import org.example.tears.Enums.JobTitle;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeLoginInfo {
    private String fullName;
    private String email;
    private String tempPassword;
    private String phoneNumber;
    private JobTitle jobTitle;
    private EmployeeCity city;
    private String employeeCode;

}