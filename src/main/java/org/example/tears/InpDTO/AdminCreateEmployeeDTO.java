package org.example.tears.InpDTO;

import lombok.Data;
import org.example.tears.Enums.EmployeeCity;
import org.example.tears.Enums.EmployeeRole;
import org.example.tears.Enums.JobTitle;

@Data
public class AdminCreateEmployeeDTO {

    private String fullName;

    private String phoneNumber;

    private JobTitle jobTitle;

    private EmployeeCity city;

    private EmployeeRole employeeRole;
}
