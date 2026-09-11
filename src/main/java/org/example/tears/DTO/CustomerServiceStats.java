package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerServiceStats {

    private Long totalCustomers;
    private Long todayTickets;
}