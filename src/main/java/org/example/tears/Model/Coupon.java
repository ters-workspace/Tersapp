package org.example.tears.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.tears.Enums.ServiceOption;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "coupons")
@AllArgsConstructor
@NoArgsConstructor
public class Coupon {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        // كود الكوبون
        @Column(unique = true, nullable = false)
        @NotBlank(message = "لايمكن ترك كوبون الخصم فارغا")
        private String code;

        // نسبة خصم %
        private Integer discountPercentage;

        // خصم ثابت بالريال
        private Integer fixedDiscount;

        private String title;

        private String description;

        // أقل مبلغ مسموح لتطبيق الكوبون
        private Integer minimumOrderPrice;

        // أقصى خصم (لو نسبة)
        private Integer maxDiscountAmount;

        // هل الكوبون مفعل؟
        private boolean active = true;

        // تاريخ الانتهاء
        @NotBlank(message = "لايمكن ترك تاريخ الانتهاء فارغ")
        private LocalDate expiryDate;

        // عدد مرات الاستخدام المسموحة
        @NotBlank(message = "لايمكن ترك عدد مرات استخدام الكوبون فارغه")
        private Integer usageLimit;

        // عدد مرات الاستخدام الحالية
        private Integer usedCount = 0;

        // هل الكوبون لمستخدم واحد فقط؟
        private boolean oneTimePerUser = false;

        // هل الكوبون لخدمة معينة؟
        @Enumerated(EnumType.STRING)
        @NotBlank(message = "لايمكن ترك نوع الخدمة فارغه")
        private ServiceOption serviceOption;

        private LocalDateTime createdAt;
    }