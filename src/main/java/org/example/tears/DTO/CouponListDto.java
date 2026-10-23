package org.example.tears.DTO;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.tears.Enums.ServiceOption;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CouponListDto {

    private Integer id;

    private String title;

    private String description;
    @Enumerated(EnumType.STRING)
    private ServiceOption serviceOption;

    private String code;

    private String discountType;

    private String discountValue;

    private Integer minimumOrderPrice;

    private Integer maxDiscountAmount;

    private Integer usedCount;

    private Integer usageLimit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDate expiryDate;

    private boolean active;
}