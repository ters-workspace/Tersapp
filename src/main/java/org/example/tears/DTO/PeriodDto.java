package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeriodDto {

    private String calendar;
    private String view;
    private Integer year;
    private Integer month;
    private String startDate;
    private String endDate;
}