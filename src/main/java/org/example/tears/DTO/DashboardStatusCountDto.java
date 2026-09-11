package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatusCountDto {

    private String section;

    private String status;

    private Long count;
}