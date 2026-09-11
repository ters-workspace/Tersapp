package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.AppStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerServiceStats {

    private Long activeEmployees;
    private Long todayTickets;
    private AppStatus appStatus;
}