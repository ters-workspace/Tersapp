package org.example.tears.Config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.tears.Model.JwtUtil;
import org.example.tears.Model.User;
import org.example.tears.Repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // No token
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {

            // =========================
            // TOKEN TYPE
            // =========================

            String tokenType = jwtUtil.getTokenType(token);

            if (!"ACCESS".equals(tokenType)) {

                SecurityContextHolder.clearContext();

                sendAuthError(
                        response,
                        "ACCESS_TOKEN_INVALID"
                );

                return;
            }

            // =========================
            // PHONE
            // =========================

            String phone = jwtUtil.getPhoneFromToken(token);

            // =========================
            // ROLE
            // =========================

            String role = jwtUtil.getRoleFromToken(token);

            if (phone == null || role == null) {

                SecurityContextHolder.clearContext();

                sendAuthError(
                        response,
                        "ACCESS_TOKEN_INVALID"
                );

                return;
            }

            // =========================
            // USER
            // =========================

            User user = userRepository
                    .findByPhoneNumber(phone)
                    .orElse(null);

            if (user == null) {

                SecurityContextHolder.clearContext();

                sendAuthError(
                        response,
                        "USER_NOT_FOUND"
                );

                return;
            }

            // =========================
            // AUTHENTICATION
            // =========================

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            )
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(auth);

            System.out.println(
                    "JWT AUTHENTICATED: "
                            + phone
                            + " | ROLE: "
                            + role
            );

            filterChain.doFilter(request, response);

        }

        // =========================
        // EXPIRED
        // =========================

        catch (ExpiredJwtException e) {

            SecurityContextHolder.clearContext();

            sendAuthError(
                    response,
                    "ACCESS_TOKEN_EXPIRED"
            );
        }

        // =========================
        // INVALID
        // =========================

        catch (JwtException | IllegalArgumentException e) {

            SecurityContextHolder.clearContext();

            sendAuthError(
                    response,
                    "ACCESS_TOKEN_INVALID"
            );
        }
    }

    private void sendAuthError(
            HttpServletResponse response,
            String error
    ) throws IOException {

        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.getWriter().write(
                """
                {
                  "success": false,
                  "error": "%s"
                }
                """.formatted(error)
        );
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path = request.getServletPath();

        return path.startsWith(
                "/api/v1/tears/auth/"
        );
    }
}