package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminCouponCustomerDto {

    private Integer customerId;
    private String fullName;
    private String phoneNumber;
}