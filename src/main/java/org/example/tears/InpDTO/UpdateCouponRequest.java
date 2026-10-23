package org.example.tears.InpDTO;
import lombok.Data;
import org.example.tears.Enums.ServiceOption;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UpdateCouponRequest {

    private String title;
    private String description;
    private ServiceOption serviceOption;
    private String code;
    private Integer discountPercentage;
    private Integer fixedDiscount;
    private Integer maxDiscountAmount;
    private Integer minimumOrderPrice;
    private Integer usageLimit;
    private Boolean newUsersOnly;
    private LocalDate expiryDate;
    private LocalDateTime updatedAt;

}