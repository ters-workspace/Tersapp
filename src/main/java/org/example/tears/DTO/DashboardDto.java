package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDto {

    private Long totalRequests;
    private BigDecimal requestsGrowthPercentage;

    private Long activeEmployees;
    private Long newEmployeesThisMonth;

    private BigDecimal totalRevenue;

    private CustomerAppStats customerApp;
    private EmployeeAppStats employeeApp;
    private CustomerServiceStats customerService;
}