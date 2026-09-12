package org.example.tears.Controller;

import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.CustomerCouponDto;
import org.example.tears.InpDTO.CreateCouponRequest;
import org.example.tears.InpDTO.UpdateCouponRequest;
import org.example.tears.InpDTO.ValidateCouponDto;
import org.example.tears.Model.Coupon;
import org.example.tears.Model.User;
import org.example.tears.OutDTO.PricingResponse;
import org.example.tears.Service.CouponService;
import org.example.tears.Service.PricingCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tears/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final PricingCalculationService pricingCalculationService;

    @PostMapping("/new")
    public Coupon create(@RequestBody CreateCouponRequest dto) {
        return couponService.create(dto);
    }

    @PatchMapping("/update/{id}")
    public Coupon update(
            @PathVariable Integer id,
            @RequestBody UpdateCouponRequest dto
    ) {
        return couponService.update(id, dto);
    }

    @GetMapping("/all")
    public List<Coupon> getAll() {
        return couponService.getAll();
    }

    @PostMapping("/validate")
    public ResponseEntity<PricingResponse> validate(
            @RequestBody ValidateCouponDto dto,
            @AuthenticationPrincipal User user
    ) {

        if (user.getCustomer() == null) {
            throw new ApiException("غير مصرح");
        }

        PricingResponse response =
                pricingCalculationService.calculateFinal(
                        dto.getServiceOption(),
                        dto.getHydraulicTruck(),
                        dto.getCouponCode(),
                        user.getCustomer()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/stats")
    public ApiResponse getCouponStats() {

        return new ApiResponse(
                true,
                "تم جلب إحصائيات الكوبونات",
                couponService.getCouponStats()
        );
    }

    @GetMapping("/admin/all")
    public ApiResponse getAllCouponsForAdmin() {

        return new ApiResponse(
                true,
                "تم جلب الكوبونات",
                couponService.getAllForAdmin()
        );
    }

    @GetMapping("/admin/search")
    public ApiResponse searchCoupons(
            @RequestParam(required = false)
            String search
    ) {

        return new ApiResponse(
                true,
                "تم البحث في الكوبونات",
                couponService.searchForAdmin(search)
        );
    }

    @PutMapping("/disable/{id}")
    public Coupon disable(@PathVariable Integer id) {
        return couponService.disable(id);
    }

    @GetMapping("/customer/coupons")
    public List<CustomerCouponDto> getMyCoupons(
            @AuthenticationPrincipal User user
    ) {

        if (user.getCustomer() == null) {
            throw new ApiException("غير مصرح");
        }

        return couponService.getCustomerValidCoupons(
                user.getCustomer()
        );
    }
}