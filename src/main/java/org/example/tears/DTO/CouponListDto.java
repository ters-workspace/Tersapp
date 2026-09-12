package org.example.tears.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CouponListDto {

    private Integer id;

    private String code;

    private String discountType;

    private String discountValue;

    private Integer usedCount;

    private Integer usageLimit;

    private LocalDate expiryDate;

    private boolean active;
}