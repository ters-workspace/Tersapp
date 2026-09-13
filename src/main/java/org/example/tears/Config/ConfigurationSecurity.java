package org.example.tears.Config;


import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ConfigurationSecurity {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public ConfigurationSecurity(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth

                        // ================= SWAGGER =================
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/",
                                "/extract-istimara/**",
                                "/chat"
                        ).permitAll()

                        // ================= AUTH =================
                        .requestMatchers(
                                "/api/v1/tears/auth/dev/admin-token"
                        ).permitAll()
                        
                        // ================= AUTH =================
                        .requestMatchers("/api/v1/tears/auth/**").permitAll()

                        // ================= CONTENT (TERMS / PRIVACY / FAQS) =================
                        .requestMatchers("/api/v1/tears/content/**").permitAll()

                        // ================= PUBLIC CAR DATA =================
                        .requestMatchers(
                                        "/api/v1/tears/cars/brands",
                                        "/api/v1/tears/cars/brands/**",
                                        "/api/v1/tears/service-request/availability",
                                        "/api/v1/tears/cars/brands/search",
                                        "/api/v1/tears/cars/models/search",
                                        "/api/v1/tears/cars/filter",
                                        "api/v1/tears/users/delete/**"
                        ).permitAll()

                        .requestMatchers(
                                "/api/v1/tears/coupons/**",
                                "/api/v1/tears/payment/**"
                        ).permitAll()

                        // ================= USER =================
                        .requestMatchers(
                                "/api/v1/tears/users/customer/**",
                                "/api/v1/tears/users/employee/**",
                                "/api/v1/tears/users/notifications",
                                "/api/v1/tears/users/change-phone/**"
                        ).authenticated()

                        // ================= CUSTOMER =================
                        .requestMatchers(
                                "/api/v1/tears/customer/**",
                                "/api/v1/tears/cars/my-car",
                                "/api/v1/tears/cars/register/**",
                                "/api/v1/tears/service-request/**",
                                "/api/v1/tears/cars/update/**",
                                "/api/v1/tears/cars/delete/**",
                                "/api/v1/tears/cars/details/**",
                                "/api/v1/tears/cars/extract-owner",
                                "/api/v1/tears/location/**",
                                "/api/v1/tears/cars/extract-user-name"

                        ).hasRole("CUSTOMER")

                        // ================= EMPLOYEE =================
                        .requestMatchers("/api/v1/tears/employee/**")
                        .hasRole("EMPLOYEE")

                        // ================= PRICING =================
                        .requestMatchers("/api/v1/tears/pricing/**")
                        .hasRole("EMPLOYEE")

                        // ================= ADMIN =================
                        .requestMatchers(
                                "/api/v1/tears/admin/**",
                                "/api/v1/tears/dashboard/admin/**",
                                "/admin/payment-settings/**"
                        ).hasRole("ADMIN")

                        // ================= STATIC =================
                        .requestMatchers("/uploads/**","brands/**", "/carimage/**").permitAll()

                        .requestMatchers("/api/v1/tears/payment/webhook")
                        .permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}