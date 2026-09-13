package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyStatisticsDto {

    private Integer monthNumber;
    private String monthName;
    private Long orders;
    private BigDecimal revenue;
}
