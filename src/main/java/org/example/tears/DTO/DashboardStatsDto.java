package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatsDto {

    private String section;

    private Long total;

    private List<DashboardStatusCountDto> statuses;
}
