package org.example.tears.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.tears.Api.ApiException;
import org.example.tears.Api.ApiResponse;
import org.example.tears.DTO.*;
import org.example.tears.Enums.UserRole;
import org.example.tears.Enums.UserStatus;
import org.example.tears.InpDTO.ChangePasswordDTO;
import org.example.tears.InpDTO.CustomerRegisterDTO;
import org.example.tears.InpDTO.LoginDTO;
import org.example.tears.Model.*;
import org.example.tears.OutDTO.AuthStatusDto;
import org.example.tears.Repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.example.tears.Model.Wallet;

import java.time.LocalDateTime;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
   // private final TwilioConfig twilioConfig;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmployeeRepository employeeRepo;
    private final WalletRepository walletRepository;

    // =========================================================
    // 1️⃣ تسجيل العميل
    // =========================================================
    public ApiResponse registerCustomer(CustomerRegisterDTO dto) {

        if (userRepo.existsByPhoneNumber(dto.getPhoneNumber()))
            throw new ApiException("رقم الجوال مستخدم مسبقا");

        User user = new User();
        user.setFullName(dto.getFullName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPassword(encoder.encode("TEMP@1234"));

        Customer customer = new Customer();
        customer.setDateOfBirth(dto.getDateOfBirth());
        customer.setUser(user);

        user.setCustomer(customer);

        User savedUser = userRepo.save(user);
        // إنشاء محفظة للعميل
        Wallet wallet = new Wallet();
        wallet.setUser(savedUser);
        wallet.setBalance(0);
        wallet.setCreatedAt(LocalDateTime.now());

        walletRepository.save(wallet);

        // ================= DEV =================
        System.out.println("OTP = 123456");

        // ================= PRODUCTION =================
        // try {
        //     Verification.creator(
        //         twilioConfig.getServiceSid(),
        //         dto.getPhoneNumber(),
        //         "sms"
        //     ).create();
        // } catch (Exception e) {
        //     throw new ApiException("Failed to send OTP");
        // }

        return new ApiResponse(
                true,
                "OTP sent to " + dto.getPhoneNumber()
        );
    }


    // =========================================================
    // 2️⃣ التحقق من OTP
    // =========================================================

    public ApiResponse verifyCustomerOtp(
            String phoneNumber,
            String otp
    ) {

        User user =
                userRepo.findByPhoneNumber(phoneNumber)
                        .orElseThrow(() ->
                                new ApiException("User not found")
                        );

        if (!otp.equals("123456")) {
            throw new ApiException("Invalid OTP");
        }
// ================= PRODUCTION =================
        // VerificationCheck check = VerificationCheck.creator(twilioConfig.getServiceSid())
        //        .setTo(phoneNumber)
        //        .setCode(otp)
        //        .create();
        //
        // if (!"approved".equalsIgnoreCase(check.getStatus()))
        //     throw new ApiException("Invalid or expired OTP");

        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        TokenResponseDto tokens =
                generateTokens(user);

        return new ApiResponse(
                true,
                "User verified successfully",
                tokens
        );
    }

    // =========================================================
    // 3️⃣ إعادة إرسال OTP
    // =========================================================
    public ApiResponse resendCustomerOtp(String phoneNumber) {

        User user = userRepo.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ApiException("User not found"));

        if (user.getRole() != UserRole.CUSTOMER)
            throw new ApiException("Not a customer");

//        if (user.getStatus() == UserStatus.ACTIVE)
//            throw new ApiException("Account already verified");

        // ================= DEV =================
        System.out.println("OTP resent = 123456");

        // ================= PRODUCTION =================
        // try {
        //     Verification.creator(
        //         twilioConfig.getServiceSid(),
        //         phoneNumber,
        //         "sms"
        //     ).create();
        // } catch (Exception e) {
        //     throw new ApiException("Failed to resend OTP");
        // }

        return new ApiResponse(true, "OTP resent to " + phoneNumber);
    }

    // =========================================================
    // 4️⃣ تسجيل دخول العميل
    // =========================================================
    public ApiResponse loginCustomer(String phoneNumber) {

        User user = userRepo.findByPhoneNumber(phoneNumber)
                .orElseThrow(() ->
                        new ApiException("Phone number not registered. Please register first.")
                );

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new ApiException("Account not active. Please verify OTP first.");

        // ================= DEV =================
        System.out.println("OTP login = 123456");

        // ================= PRODUCTION =================
        // Verification.creator(
        //     twilioConfig.getServiceSid(),
        //     phoneNumber,
        //     "sms"
        // ).create();

        return new ApiResponse(true, "OTP sent to " + phoneNumber);
    }

    // =========================================================
    // 5️⃣ تسجيل دخول الموظف
    // =========================================================
    public ApiResponse loginEmployee(LoginDTO dto) {

        User user = userRepo
                .findByEmail(
                        dto.getEmail())
                .orElseThrow(() ->
                        new ApiException("بيانات الدخول غير صحيحة")
                );


        System.out.println("PASSWORD MATCH = "
                + encoder.matches(
                dto.getPassword(),
                user.getPassword()
        ));

        if (
                user.getRole() != UserRole.EMPLOYEE
                        &&
                        user.getRole() != UserRole.ADMIN
        ) {
            throw new ApiException("غير مصرح");
        }

        if (!encoder.matches(
                dto.getPassword(),
                user.getPassword()
        )) {
            throw new ApiException("بيانات الدخول غير صحيحة");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ApiException("الحساب معطل");
        }

        // DEV OTP
        System.out.println("Employee OTP = 123456");

        return new ApiResponse(
                true,
                "OTP sent successfully"
        );
    }

    // =========================================================
    // 6️⃣ استعاده كلمة المرور
    // =========================================================

    public void sendResetPasswordOtp(SendOtpDto dto) {

        User user = userRepo.findByPhoneNumber(dto.getPhoneNumber())
                .orElseThrow(() ->
                        new ApiException("رقم الجوال غير مسجل"));

        if (user.getEmployee() == null) {
            throw new ApiException("هذه الخدمة خاصة بالموظفين");
        }

        // حالياً مؤقت
        // بعدين تستبدله بـ Twilio
        System.out.println("OTP = 123456");
    }

    @Transactional
    public VerifyOtpResponse verifyResetPasswordOtp(
    VerifyOtpDTO dto
    ) {

        User user = userRepo.findByPhoneNumber(dto.getPhoneNumber())
                .orElseThrow(() ->
                        new ApiException("رقم الجوال غير مسجل"));

        if (user.getEmployee() == null) {
            throw new ApiException("هذه الخدمة خاصة بالموظفين");
        }

        // مؤقت
        if (!dto.getOtp().equals("123456")) {
            throw new ApiException("رمز التحقق غير صحيح");
        }

        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByUser(user)
                        .orElse(new PasswordResetToken());

        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        token.setUsed(false);

        passwordResetTokenRepository.save(token);

        return new VerifyOtpResponse(token.getToken());
    }


    @Transactional
    public void resetPassword(
            ResetPasswordDto dto
    ) {

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ApiException("كلمتا المرور غير متطابقتين");
        }

        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByToken(dto.getResetToken())
                        .orElseThrow(() ->
                                new ApiException("رمز إعادة التعيين غير صالح"));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new ApiException("تم استخدام رمز إعادة التعيين");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException("انتهت صلاحية رمز إعادة التعيين");
        }

        User user = token.getUser();

        user.setPassword(
                encoder.encode(dto.getNewPassword())
        );

        if (user.getEmployee() != null) {

            user.getEmployee().setMustChangePassword(false);
        }

        userRepo.save(user);

        token.setUsed(true);

        passwordResetTokenRepository.save(token);
    }

    // =========================================================
    // 6️⃣ تغيير كلمة المرور
    // =========================================================
    @Transactional
    public void changePassword(
            User user,
            ChangePasswordDTO dto
    ) {

        if (user == null) {
            throw new ApiException("المستخدم غير موجود");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ApiException("كلمتا المرور غير متطابقتين");
        }

        user.setPassword(
                encoder.encode(dto.getNewPassword())
        );

        if (user.getEmployee() != null) {
            user.getEmployee().setMustChangePassword(false);
            employeeRepo.save(user.getEmployee());
        }

        userRepo.save(user);
    }

    public TokenResponseDto verifyEmployeeOtp(
            String email,
            String otp
    ) {

        User user =
                userRepo.findByEmail(email)
                        .orElseThrow(() ->
                                new ApiException("User not found")
                        );

        if (!otp.equals("123456")) {
            throw new ApiException("Invalid OTP");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        return generateTokens(user);
    }


    // =========================================================
    // 8️⃣ جلب المستخدم من التوكن
    // =========================================================
    public User getAuthenticatedUser(HttpServletRequest request) {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer "))
            throw new ApiException("Missing token", 401);

        String token = header.substring(7);
        String phone = jwtUtil.getPhoneFromToken(token);

        return userRepo.findByPhoneNumber(phone)
                .orElseThrow(() -> new ApiException("User not found", 404));
    }

    // =========================================================
    // 9️⃣ حذف مستخدم (اختياري)
    // =========================================================
    @Transactional
    public ApiResponse deleteByPhone(String phone) {
        userRepo.deleteByPhoneNumber(phone);
        return new ApiResponse(true, "Deleted");
    }

    // =========================================================
    // 🔟 حالة المستخدم
    // =========================================================
    public ApiResponse getMe(HttpServletRequest request) {

        try {

            User user = getAuthenticatedUser(request);

            System.out.println("USER ID = " + user.getId());
            System.out.println("USER NAME = " + user.getFullName());
            System.out.println("USER ROLE = " + user.getRole());

            System.out.println(
                    "EMPLOYEE = " +
                            (user.getEmployee() != null
                                    ? user.getEmployee().getId()
                                    : "NULL")
            );

            System.out.println(
                    "CUSTOMER = " +
                            (user.getCustomer() != null
                                    ? user.getCustomer().getId()
                                    : "NULL")
            );

            Integer employeeId =
                    user.getEmployee() != null
                            ? user.getEmployee().getId()
                            : null;

            Integer customerId =
                    user.getCustomer() != null
                            ? user.getCustomer().getId()
                            : null;

            return new ApiResponse(
                    true,
                    new AuthStatusDto(
                            true,
                            user.getId(),
                            employeeId,
                            customerId,
                            user.getFullName(),
                            user.getRole().name()
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return new ApiResponse(
                    false,
                    new AuthStatusDto(
                            false,
                            null,
                            null,
                            null,
                            null,
                            "GUEST"
                    )
            );
        }
    }


    @Transactional
    public void resetAdminPassword(ResetAdminPasswordDto dto) {

        User admin = userRepo.findByEmail(dto.getEmail())
                .orElseThrow(() ->
                        new ApiException("الأدمن غير موجود"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ApiException("هذا المستخدم ليس Admin");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ApiException("كلمتا المرور غير متطابقتين");
        }

        admin.setPassword(
                encoder.encode(dto.getNewPassword())
        );

        admin.setStatus(UserStatus.ACTIVE);

        userRepo.save(admin);
    }

    @Transactional
    public TokenResponseDto generateTokens(
            User user
    ) {

        // =========================
        // ACCESS TOKEN
        // =========================

        String accessToken =
                jwtUtil.generateAccessToken(

                        user.getPhoneNumber(),

                        user.getRole().name()
                );


        // =========================
        // REFRESH TOKEN
        // =========================

        String refreshToken =
                jwtUtil.generateRefreshToken(
                        user.getPhoneNumber()
                );


        // =========================
        // SAVE REFRESH TOKEN
        // =========================

        RefreshToken token =
                new RefreshToken();

        token.setUser(user);

        token.setToken(
                refreshToken
        );

        token.setExpiresAt(
                LocalDateTime.now()
                        .plusSeconds(
                                jwtUtil.getRefreshExpirationSeconds()
                        )
        );

        token.setRevoked(false);

        token.setCreatedAt(
                LocalDateTime.now()
        );

        refreshTokenRepository.save(token);


        return new TokenResponseDto(

                accessToken,

                refreshToken,

                jwtUtil.getAccessExpirationSeconds(),

                jwtUtil.getRefreshExpirationSeconds(),

                "Bearer"
        );
    }

    @Transactional
    public TokenResponseDto refreshToken(
            String refreshToken
    ) {

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByToken(refreshToken)
                        .orElseThrow(() ->
                                new ApiException(
                                        "REFRESH_TOKEN_INVALID",
                                        401
                                )
                        );


        // =========================
        // REVOKED
        // =========================

        if (Boolean.TRUE.equals(
                storedToken.getRevoked()
        )) {

            throw new ApiException(
                    "REFRESH_TOKEN_INVALID",
                    401
            );
        }


        // =========================
        // DB EXPIRATION
        // =========================

        if (storedToken.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            storedToken.setRevoked(true);

            refreshTokenRepository.save(
                    storedToken
            );

            throw new ApiException(
                    "REFRESH_TOKEN_INVALID",
                    401
            );
        }


        // =========================
        // JWT VALIDATION
        // =========================

        try {

            String tokenType =
                    jwtUtil.getTokenType(
                            refreshToken
                    );

            if (!"REFRESH".equals(
                    tokenType
            )) {

                throw new ApiException(
                        "REFRESH_TOKEN_INVALID",
                        401
                );
            }

        } catch (ApiException e) {

            throw e;

        } catch (Exception e) {

            throw new ApiException(
                    "REFRESH_TOKEN_INVALID",
                    401
            );
        }


        User user =
                storedToken.getUser();


        // =========================
        // ROTATION
        // =========================

        storedToken.setRevoked(true);

        refreshTokenRepository.save(
                storedToken
        );


        return generateTokens(
                user
        );
    }

    @Transactional
    public void logout(
            String refreshToken
    ) {

        refreshTokenRepository
                .findByToken(refreshToken)
                .ifPresent(token -> {

                    token.setRevoked(true);

                    refreshTokenRepository.save(
                            token
                    );
                });
    }

    public TokenResponseDto generateDevAdminTokens() {

        User admin =
                userRepo.findByPhoneNumber("+966500000009")
                        .orElseThrow(() ->
                                new ApiException("Admin not found")
                        );

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ApiException("User is not ADMIN");
        }

        return generateTokens(admin);
    }
}