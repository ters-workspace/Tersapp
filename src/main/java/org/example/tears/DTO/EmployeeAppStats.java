package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.AppStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeAppStats {

    private Long activeEmployees;
    private Long todayWorkedRequests;
    private AppStatus appStatus;
}