package org.example.tears.InpDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.ServiceOption;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCouponRequest {

    private String code;

    private String title;

    private String description;

    private Integer discountPercentage;

    private Integer fixedDiscount;

    private Integer usageLimit;

    private Integer minimumOrderPrice;

    private Integer maxDiscountAmount;

    private LocalDate expiryDate;

    private Boolean newUsersOnly;

    private Boolean oneTimePerUser = false;

    private List<Integer> eligibleCustomerIds;

    private ServiceOption serviceOption;
}