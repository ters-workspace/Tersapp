package org.example.tears.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        @Min(value = 1, message = "نسبة الخصم يجب أن تكون أكبر من صفر")
        @Max(value = 100, message = "نسبة الخصم لا يمكن أن تتجاوز 100%")
        private Integer discountPercentage;

        // خصم ثابت بالريال
        @Min(value = 1, message = "الخصم الثابت يجب أن يكون أكبر من صفر")
        private Integer fixedDiscount;

        private String title;

        private String description;

        // أقل مبلغ مسموح لتطبيق الكوبون
        @Min(value = 0, message = "الحد الأدنى للطلب لا يمكن أن يكون سالبا")
        private Integer minimumOrderPrice;

        // أقصى خصم لو كان الكوبون نسبة
        @Min(value = 0, message = "أقصى خصم لا يمكن أن يكون سالبا")
        private Integer maxDiscountAmount;

        // هل الكوبون مفعل؟
        private boolean active = true;

        // تاريخ الانتهاء
        @NotNull(message = "لايمكن ترك تاريخ الانتهاء فارغا")
        private LocalDate expiryDate;

        // عدد مرات الاستخدام المسموحة
        @NotNull(message = "لايمكن ترك عدد مرات استخدام الكوبون فارغا")
        @Min(value = 1, message = "عدد مرات الاستخدام يجب أن يكون أكبر من صفر")
        private Integer usageLimit;

        // عدد مرات الاستخدام الحالية
        @Min(value = 0, message = "عدد الاستخدامات لا يمكن أن يكون سالبا")
        private Integer usedCount = 0;

        // هل الكوبون لمستخدم واحد فقط؟
        private boolean oneTimePerUser = false;

        // الخدمة التي ينطبق عليها الكوبون
        @NotNull(message = "لايمكن ترك نوع الخدمة فارغا")
        @Enumerated(EnumType.STRING)
        private ServiceOption serviceOption;

        private LocalDateTime createdAt;
}