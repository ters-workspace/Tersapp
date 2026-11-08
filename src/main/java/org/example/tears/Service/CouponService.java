
package org.example.tears.Service;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.DTO.CouponListDto;
import org.example.tears.DTO.CouponStatsDto;
import org.example.tears.DTO.CustomerCouponDto;
import org.example.tears.Enums.CouponCustomerType;
import org.example.tears.Enums.ServiceOption;
import org.example.tears.InpDTO.CreateCouponRequest;
import org.example.tears.InpDTO.UpdateCouponRequest;
import org.example.tears.Model.*;
import org.example.tears.Repository.CarServiceRequestRepository;
import org.example.tears.Repository.CouponRepository;
import org.example.tears.Repository.CouponUsageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CarServiceRequestRepository carServiceRequestRepository;

    // =========================
    // Create Coupon
    // =========================

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

        coupon.setCustomerType(
                dto.getCustomerType() != null
                        ? dto.getCustomerType()
                        : CouponCustomerType.ALL_CUSTOMERS
        );

        coupon.setOneTimePerUser(
                Boolean.TRUE.equals(dto.getOneTimePerUser())
        );

        LocalDateTime now = TimeUtil.now();

        coupon.setCreatedAt(now);

        if (dto.getActivationDate() == null ||
                !dto.getActivationDate().isAfter(now)) {

            coupon.setActivationDate(now);
            coupon.setActive(true);

        } else {

            coupon.setActivationDate(dto.getActivationDate());
            coupon.setActive(false);
        }
        return couponRepository.save(coupon);
    }

    // =========================
    // Update Coupon
    // =========================

    public Coupon update(Integer id, UpdateCouponRequest dto) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException("الكوبون غير موجود"));

        if (dto.getDiscountPercentage() != null) {
            coupon.setDiscountPercentage(dto.getDiscountPercentage());
        }

        if (dto.getTitle() != null) {
            coupon.setTitle(dto.getTitle());
        }

        if (dto.getDescription() != null) {
            coupon.setDescription(dto.getDescription());
        }

        if (dto.getCode() != null) {
            coupon.setCode(dto.getCode());
        }

        if (dto.getServiceOption() != null) {
            coupon.setServiceOption(dto.getServiceOption());
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

        if (dto.getCustomerType() != null) {
            coupon.setCustomerType(dto.getCustomerType());
        }

        if (dto.getActivationDate() != null) {

            coupon.setActivationDate(dto.getActivationDate());

            coupon.setActive(
                    !dto.getActivationDate().isAfter(TimeUtil.now())
            );
        }
        coupon.setUpdatedAt(TimeUtil.now());

        return couponRepository.save(coupon);
    }

    // =========================
    // Validate Coupon
    // =========================

    public Coupon validate(
            String code,
            Double total,
            ServiceOption option,
            Customer customer
    ) {

        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() ->
                        new ApiException("الكوبون غير موجود")
                );

        // Active
        if (!coupon.isActive()) {
            throw new ApiException("الكوبون غير متاح");
        }

        // Customer Type
        if (!isCustomerEligible(coupon, customer)) {
            throw new ApiException(
                    "هذا الكوبون غير متاح لفئة العملاء الخاصة بك"
            );
        }

        // Expiry
        if (coupon.getExpiryDate() != null &&
                coupon.getExpiryDate().isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) {

            throw new ApiException("الكوبون منتهي");
        }

        // Service
        if (!isServiceApplicable(coupon, option)) {
            throw new ApiException(
                    "هذا الكوبون لا ينطبق على هذه الخدمة"
            );
        }

        // Minimum Order
        if (coupon.getMinimumOrderPrice() != null &&
                total < coupon.getMinimumOrderPrice()) {

            throw new ApiException(
                    "لم يصل الحد الادنى للمبلغ"
            );
        }

        // Global Usage Limit
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

        // One Time Per Customer
        if (coupon.isOneTimePerUser()) {

            if (customer == null || customer.getId() == null) {
                throw new ApiException(
                        "تعذر التحقق من استخدام الكوبون للعميل"
                );
            }

            boolean alreadyUsed =
                    couponUsageRepository.existsByCouponIdAndCustomerId(
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

    // =========================
    // Customer Eligibility
    // =========================

    private boolean isCustomerEligible(
            Coupon coupon,
            Customer customer
    ) {

        if (coupon.getCustomerType() == null ||
                coupon.getCustomerType() == CouponCustomerType.ALL_CUSTOMERS) {
            return true;
        }

        if (customer == null || customer.getId() == null) {
            return false;
        }

        boolean hasPreviousRequest =
                carServiceRequestRepository.existsByCustomer_Id(
                        customer.getId()
                );

        return switch (coupon.getCustomerType()) {

            case ALL_CUSTOMERS -> true;

            case NEW_CUSTOMERS -> !hasPreviousRequest;

            case EXISTING_CUSTOMERS -> hasPreviousRequest;
        };
    }

    // =========================
    // Service Eligibility
    // =========================

    private boolean isServiceApplicable(
            Coupon coupon,
            ServiceOption serviceOption
    ) {

        return coupon.getServiceOption() == ServiceOption.ALL
                || coupon.getServiceOption() == serviceOption;
    }

    // =========================
    // Get All Coupons
    // =========================

    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    // =========================
    // Disable Coupon
    // =========================

    public Coupon disableCoupon(Integer id) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException("غير موجود"));

        coupon.setActive(false);

        return couponRepository.save(coupon);
    }

    // =========================
    // Enable Coupon
    // =========================

    public Coupon enableCoupon(Integer id) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException("غير موجود"));

        coupon.setActive(true);

        return couponRepository.save(coupon);
    }

    // =========================
    // Get Valid Coupons For Customer
    // =========================

    public List<CustomerCouponDto> getCustomerValidCoupons(
            Customer customer
    ) {

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Riyadh"));

        return couponRepository.findAll()
                .stream()

                // Active
                .filter(Coupon::isActive)

                // Not Expired
                .filter(coupon ->
                        coupon.getExpiryDate() == null ||
                                !coupon.getExpiryDate().isBefore(today)
                )

                // Global Usage Limit
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

                // One Time Per User
                .filter(coupon -> {

                    if (!coupon.isOneTimePerUser()) {
                        return true;
                    }

                    if (customer == null || customer.getId() == null) {
                        return false;
                    }

                    return !couponUsageRepository
                            .existsByCouponIdAndCustomerId(
                                    coupon.getId(),
                                    customer.getId()
                            );
                })

                // Customer Type
                .filter(coupon ->
                        isCustomerEligible(coupon, customer)
                )

                // Map To DTO
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

    // =========================
    // Record Coupon Usage
    // =========================

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
                    couponUsageRepository.existsByCouponIdAndCustomerId(
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
        usage.setUsedAt(TimeUtil.now());

        couponUsageRepository.save(usage);

        // Increase usage count
        coupon.setUsedCount(
                coupon.getUsedCount() == null
                        ? 1
                        : coupon.getUsedCount() + 1
        );

        couponRepository.save(coupon);
    }

    // =========================
    // Coupon Statistics
    // =========================

    public CouponStatsDto getCouponStats() {

        long totalCoupons = couponRepository.count();

        long activeCoupons = couponRepository.countByActiveTrue();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Riyadh"));

        LocalDate sevenDaysLater = today.plusDays(7);

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

    // =========================
    // Convert Coupon To Admin DTO
    // =========================

    private CouponListDto toCouponListDto(Coupon coupon) {

        CouponListDto dto = new CouponListDto();

        dto.setId(coupon.getId());

        dto.setCode(coupon.getCode());

        // Discount Type And Value
        if (coupon.getDiscountPercentage() != null) {

            dto.setDiscountType("PERCENTAGE");

            dto.setDiscountValue(
                    coupon.getDiscountPercentage() + "%"
            );

        } else if (coupon.getFixedDiscount() != null) {

            dto.setDiscountType("FIXED");

            dto.setDiscountValue(
                    coupon.getFixedDiscount() + " SAR"
            );

        } else {

            dto.setDiscountType("UNKNOWN");

            dto.setDiscountValue("0");
        }

        dto.setUsedCount(
                coupon.getUsedCount() == null
                        ? 0
                        : coupon.getUsedCount()
        );

        dto.setUsageLimit(coupon.getUsageLimit());

        dto.setExpiryDate(coupon.getExpiryDate());

        dto.setActive(coupon.isActive());

        dto.setCustomerType(coupon.getCustomerType());

        return dto;
    }

    // =========================
    // Get All For Admin
    // =========================

    public List<CouponListDto> getAllForAdmin() {

        return couponRepository.findAll()
                .stream()
                .map(this::toCouponListDto)
                .toList();
    }

    // =========================
    // Search For Admin
    // =========================

    public List<CouponListDto> searchForAdmin(String search) {

        if (search == null || search.isBlank()) {
            return getAllForAdmin();
        }

        return couponRepository.searchCoupons(search.trim())
                .stream()
                .map(this::toCouponListDto)
                .toList();
    }

    // =========================
    // Delete Coupon
    // =========================

    @Transactional
    public void deleteCoupon(Integer id) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() ->
                        new ApiException("الكوبون غير موجود")
                );

        long usageCount =
                couponUsageRepository.countByCouponId(id);

        if (usageCount > 0) {
            throw new ApiException(
                    "لا يمكن حذف الكوبون لأنه مستخدم سابقًا، يمكنك تعطيله بدلًا من ذلك"
            );
        }

        couponRepository.delete(coupon);
    }

        @Scheduled(fixedDelay = 60000)
        @Transactional
        public void activateScheduledCoupons() {

            LocalDateTime now = TimeUtil.now();

            List<Coupon> coupons =
                    couponRepository
                            .findAllByActiveFalseAndActivationDateLessThanEqual(now);

            if (coupons.isEmpty()) {
                return;
            }

            for (Coupon coupon : coupons) {
                coupon.setActive(true);
            }

            couponRepository.saveAll(coupons);
        }
}
