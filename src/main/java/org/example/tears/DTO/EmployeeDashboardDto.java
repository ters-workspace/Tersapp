package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeDashboardDto {

    private Long totalEmployees;
    private Long technicians;
    private Long pricingEmployees;
    private Long customerServiceEmployees;

    private List<EmployeeListDto> employees;
}