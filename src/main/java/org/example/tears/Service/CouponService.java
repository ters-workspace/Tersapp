package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.CouponListDto;
import org.example.tears.DTO.CouponStatsDto;
import org.example.tears.DTO.CustomerCouponDto;
import org.example.tears.Enums.ServiceOption;
import org.example.tears.InpDTO.CreateCouponRequest;
import org.example.tears.InpDTO.UpdateCouponRequest;
import org.example.tears.Model.CarServiceRequest;
import org.example.tears.Model.Coupon;
import org.example.tears.Model.CouponUsage;
import org.example.tears.Model.Customer;
import org.example.tears.Repository.CouponRepository;
import org.example.tears.Repository.CouponUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    public Coupon create(CreateCouponRequest dto) {

        if (couponRepository.findByCodeIgnoreCase(dto.getCode()).isPresent()) {
            throw new ApiException("الكوبون موجود مسبقًا");
        }

        Coupon coupon = new Coupon();

        coupon.setCode(dto.getCode().toUpperCase());
        coupon.setActive(true);
        coupon.setTitle(dto.getTitle());
        coupon.setDescription(dto.getDescription());
        coupon.setDiscountPercentage(dto.getDiscountPercentage());
        coupon.setFixedDiscount(dto.getFixedDiscount());
        coupon.setUsageLimit(dto.getUsageLimit());
        coupon.setMinimumOrderPrice(dto.getMinimumOrderPrice());
        coupon.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        coupon.setExpiryDate(dto.getExpiryDate());
        coupon.setServiceOption(dto.getServiceOption());
        coupon.setCreatedAt(LocalDateTime.now());

        return couponRepository.save(coupon);
    }

    public Coupon update(Integer id, UpdateCouponRequest dto) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException("الكوبون غير موجود"));

        if (dto.getDiscountPercentage() != null) {
            coupon.setDiscountPercentage(dto.getDiscountPercentage());
        }

        if (dto.getFixedDiscount() != null) {
            coupon.setFixedDiscount(dto.getFixedDiscount());
        }

        if (dto.getUsageLimit() != null) {
            coupon.setUsageLimit(dto.getUsageLimit());
        }

        if (dto.getMinimumOrderPrice() != null) {
            coupon.setMinimumOrderPrice(dto.getMinimumOrderPrice());
        }

        if (dto.getMaxDiscountAmount() != null) {
            coupon.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        }

        if (dto.getExpiryDate() != null) {
            coupon.setExpiryDate(dto.getExpiryDate());
        }

        if (dto.getActive() != null) {
            coupon.setActive(dto.getActive());
        }

        return couponRepository.save(coupon);
    }

    public Coupon validate(
            String code,
            Double total,
            ServiceOption option,
            Customer customer
    ) {

        Coupon coupon =
                couponRepository.findByCodeIgnoreCase(code)
                        .orElseThrow(() ->
                                new ApiException("الكوبون غير موجود")
                        );

        // =========================
        // Active
        // =========================

        if (!coupon.isActive()) {
            throw new ApiException("الكوبون غير متاح");
        }

        // =========================
        // Expiry
        // =========================

        if (coupon.getExpiryDate() != null &&
                coupon.getExpiryDate().isBefore(LocalDate.now())) {

            throw new ApiException("الكوبون منتهي");
        }

        // =========================
        // Service
        // =========================

        if (coupon.getServiceOption() != null &&
                coupon.getServiceOption() != option) {

            throw new ApiException(
                    "الكوبون غير متاح لهاذي الخدمة"
            );
        }

        // =========================
        // Minimum order
        // =========================

        if (coupon.getMinimumOrderPrice() != null &&
                total < coupon.getMinimumOrderPrice()) {

            throw new ApiException(
                    "لم يصل الحد الادنى للمبلغ"
            );
        }

        // =========================
        // Global usage limit
        // =========================

        if (coupon.getUsageLimit() != null) {

            long usedCount =
                    couponUsageRepository.countByCouponId(
                            coupon.getId()
                    );

            if (usedCount >= coupon.getUsageLimit()) {

                throw new ApiException(
                        "انتهت استخدامات هذا الكوبون"
                );
            }
        }

        // =========================
        // One time per customer
        // =========================

        if (coupon.isOneTimePerUser()) {

            boolean alreadyUsed =
                    couponUsageRepository
                            .existsByCouponIdAndCustomerId(
                                    coupon.getId(),
                                    customer.getId()
                            );

            if (alreadyUsed) {

                throw new ApiException(
                        "تم استخدام هذا الكوبون مسبقًا"
                );
            }
        }

        return coupon;
    }

    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    public Coupon disable(Integer id) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException("غير موجود"));

        coupon.setActive(false);

        return couponRepository.save(coupon);
    }

    public List<CustomerCouponDto> getCustomerValidCoupons(
            Customer customer
    ) {

        LocalDate today = LocalDate.now();

        return couponRepository.findAll()
                .stream()

                // الكوبون مفعل
                .filter(Coupon::isActive)

                // غير منتهي
                .filter(coupon ->
                        coupon.getExpiryDate() == null ||
                                !coupon.getExpiryDate().isBefore(today)
                )

                // ما تجاوز الاستخدامات العامة
                .filter(coupon -> {

                    if (coupon.getUsageLimit() == null) {
                        return true;
                    }

                    long usedCount =
                            couponUsageRepository.countByCouponId(
                                    coupon.getId()
                            );

                    return usedCount < coupon.getUsageLimit();
                })

                // لو One Time Per User:
                // العميل ما يكون استخدمه قبل
                .filter(coupon -> {

                    if (!coupon.isOneTimePerUser()) {
                        return true;
                    }

                    return !couponUsageRepository
                            .existsByCouponIdAndCustomerId(
                                    coupon.getId(),
                                    customer.getId()
                            );
                })

                .map(coupon -> {

                    CustomerCouponDto dto =
                            new CustomerCouponDto();

                    dto.setId(coupon.getId());
                    dto.setCode(coupon.getCode());

                    dto.setDiscountPercentage(
                            coupon.getDiscountPercentage()
                    );

                    dto.setTitle(coupon.getTitle());

                    dto.setDescription(coupon.getDescription());

                    dto.setFixedDiscount(
                            coupon.getFixedDiscount()
                    );

                    dto.setMaxDiscountAmount(
                            coupon.getMaxDiscountAmount()
                    );

                    dto.setExpiryDate(
                            coupon.getExpiryDate()
                    );

                    dto.setServiceOption(
                            coupon.getServiceOption()
                    );

                    return dto;
                })

                .toList();
    }

    @Transactional
    public void recordCouponUsage(
            Coupon coupon,
            Customer customer,
            CarServiceRequest request
    ) {

        if (coupon.getUsageLimit() != null) {

            long usedCount =
                    couponUsageRepository.countByCouponId(
                            coupon.getId()
                    );

            if (usedCount >= coupon.getUsageLimit()) {
                throw new ApiException(
                        "انتهت استخدامات هذا الكوبون"
                );
            }
        }

        if (coupon.isOneTimePerUser()) {

            boolean alreadyUsed =
                    couponUsageRepository
                            .existsByCouponIdAndCustomerId(
                                    coupon.getId(),
                                    customer.getId()
                            );

            if (alreadyUsed) {
                throw new ApiException(
                        "تم استخدام هذا الكوبون مسبقًا"
                );
            }
        }

        CouponUsage usage = new CouponUsage();

        usage.setCoupon(coupon);
        usage.setCustomer(customer);
        usage.setRequest(request);
        usage.setUsedAt(LocalDateTime.now());

        couponUsageRepository.save(usage);

        // زيادة العداد
        coupon.setUsedCount(
                coupon.getUsedCount() == null
                        ? 1
                        : coupon.getUsedCount() + 1
        );

        couponRepository.save(coupon);
    }

    public CouponStatsDto getCouponStats() {

        long totalCoupons =
                couponRepository.count();

        long activeCoupons =
                couponRepository.countByActiveTrue();

        LocalDate today =
                LocalDate.now();

        LocalDate sevenDaysLater =
                today.plusDays(7);

        long expiringSoon =
                couponRepository
                        .countByActiveTrueAndExpiryDateGreaterThanEqualAndExpiryDateLessThanEqual(
                                today,
                                sevenDaysLater
                        );

        return new CouponStatsDto(
                totalCoupons,
                activeCoupons,
                expiringSoon
        );
    }

    private CouponListDto toCouponListDto(
            Coupon coupon
    ) {

        CouponListDto dto =
                new CouponListDto();

        dto.setId(
                coupon.getId()
        );

        dto.setCode(
                coupon.getCode()
        );

        // نوع وقيمة الخصم
        if (coupon.getDiscountPercentage() != null) {

            dto.setDiscountType(
                    "PERCENTAGE"
            );

            dto.setDiscountValue(
                    coupon.getDiscountPercentage() + "%"
            );

        } else if (coupon.getFixedDiscount() != null) {

            dto.setDiscountType(
                    "FIXED"
            );

            dto.setDiscountValue(
                    coupon.getFixedDiscount() + " SAR"
            );

        } else {

            dto.setDiscountType(
                    "UNKNOWN"
            );

            dto.setDiscountValue(
                    "0"
            );
        }

        dto.setUsedCount(
                coupon.getUsedCount() == null
                        ? 0
                        : coupon.getUsedCount()
        );

        dto.setUsageLimit(
                coupon.getUsageLimit()
        );

        dto.setExpiryDate(
                coupon.getExpiryDate()
        );

        dto.setActive(
                coupon.isActive()
        );

        return dto;
    }
    public List<CouponListDto> getAllForAdmin() {

        return couponRepository
                .findAll()
                .stream()
                .map(this::toCouponListDto)
                .toList();
    }

    public List<CouponListDto> searchForAdmin(
            String search
    ) {

        if (search == null ||
                search.isBlank()) {

            return getAllForAdmin();
        }

        return couponRepository
                .searchCoupons(
                        search.trim()
                )
                .stream()
                .map(this::toCouponListDto)
                .toList();
    }

}