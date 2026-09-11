package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.AppStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerAppStats {

    private Long activeCustomers;
    private Long todayRequests;
    private AppStatus appStatus;
}