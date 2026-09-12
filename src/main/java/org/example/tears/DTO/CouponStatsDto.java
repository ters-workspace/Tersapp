package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CouponStatsDto {

    private Long totalCoupons;

    private Long activeCoupons;

    private Long expiringSoon;
}