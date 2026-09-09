package org.example.tears.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.*;
import org.example.tears.InpDTO.ChangePasswordDTO;
import org.example.tears.InpDTO.CustomerRegisterDTO;
import org.example.tears.InpDTO.LoginDTO;
import org.example.tears.Model.JwtUtil;
import org.example.tears.Model.User;
import org.example.tears.Service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("api/v1/tears/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    // ================= Customer =================
    // تسجيل عميل جديد + إرسال OTP
    @PostMapping("/customer/register")
    public ApiResponse registerCustomer(@Valid @RequestBody CustomerRegisterDTO dto) {
        return authService.registerCustomer(dto);
    }

    // تفعيل حساب العميل بالـ OTP
    @PostMapping("/customer/verify")
    public ApiResponse verifyCustomerOtp(@RequestBody VerifyOtpDTO dto) {
        return authService.verifyCustomerOtp(
                dto.getPhoneNumber(),
                dto.getOtp()
        );
    }

    // إعادة إرسال OTP للعميل
    @PostMapping("/customer/resend-otp")
    public ApiResponse resendCustomerOtp(@RequestBody PhoneNumberDTO dto) {
        return authService.resendCustomerOtp(dto.getPhoneNumber());
    }

    @PostMapping("/dev/admin-token")
    public ResponseEntity<?> adminToken() {

        return ResponseEntity.ok(
                authService.generateDevAdminTokens()
        );
    }

    // ================= General Login =================

    // تسجيل دخول عميل
    @PostMapping("/customer/login/send-otp")
    public ApiResponse loginCustomer(@RequestBody PhoneNumberDTO dto) {
        return authService.loginCustomer(dto.getPhoneNumber());
    }

    // تسجيل دخول موظف
    @PostMapping("/employee/login")
    public ApiResponse loginEmployee(@RequestBody LoginDTO dto) {
        return authService.loginEmployee(dto);
    }

    // ================= OTP Password Reset (Employee) =================


    @PostMapping("/password/reset/send-otp")
    public ApiResponse sendResetPasswordOtp(
            @RequestBody @Valid SendOtpDto dto
    ){

        authService.sendResetPasswordOtp(dto);

        return new ApiResponse(
                true,
                "تم إرسال رمز التحقق"
        );
    }


    @PostMapping("/password/reset/verify-otp")
    public ApiResponse verifyOtp(
            @RequestBody @Valid VerifyOtpDTO dto
    ){

        VerifyOtpResponse response =
                authService.verifyResetPasswordOtp(dto);

        return new ApiResponse(
                true,
                "تم التحقق من الرمز",
                response
        );
    }

    @PostMapping("/password/reset")
    public ApiResponse resetPassword(
            @RequestBody @Valid ResetPasswordDto dto
    ){

        authService.resetPassword(dto);

        return new ApiResponse(
                true,
                "تم تغيير كلمة المرور بنجاح"
        );
    }




    // ================= Change Password =================
    // تغيير كلمة المرور بعد تسجيل الدخول

    @PutMapping("/change-password")
    public ApiResponse changePassword(
            @RequestBody ChangePasswordDTO dto,
            HttpServletRequest request
    ) {
        User user = authService.getAuthenticatedUser(request);

        authService.changePassword(user, dto);

        return new ApiResponse(
                true,
                "تم تغيير كلمة المرور بنجاح"
        );
    }

    @PostMapping("/employee/verify")
    public ResponseEntity<TokenResponseDto> verifyEmployeeOtp(
            @RequestBody VerifyEmployeeOtpDTO dto
    ) {

        return ResponseEntity.ok(
                authService.verifyEmployeeOtp(
                        dto.getEmail(),
                        dto.getOtp()
                )
        );
    }
    // ================= Get Logged User =================

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> me(HttpServletRequest request) {
        return ResponseEntity.ok(authService.getMe(request));
    }

    @DeleteMapping("/dev/delete/{phone}")
    public ApiResponse deleteByPhone(@PathVariable String phone) {
        authService.deleteByPhone(phone);
        return new ApiResponse(true,"Deleted");
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refreshToken(
            @RequestBody RefreshTokenRequestDto dto
    ) {

        return ResponseEntity.ok(

                authService.refreshToken(
                        dto.getRefreshToken()
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody RefreshTokenRequestDto dto
    ) {

        authService.logout(
                dto.getRefreshToken()
        );

        return ResponseEntity.noContent()
                .build();
    }
}
