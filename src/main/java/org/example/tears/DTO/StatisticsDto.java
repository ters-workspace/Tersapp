package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsDto {

    private Long totalOrders;
    private BigDecimal ordersGrowthPercentage;

    private BigDecimal revenue;
    private BigDecimal revenueGrowthPercentage;

    private Long newCustomers;
    private Long activeCustomers;

    private List<MonthlyStatisticsDto> monthlyStatistics;
    private List<HourlyOrdersDto> hourlyOrders;

    private PeriodDto period;
}