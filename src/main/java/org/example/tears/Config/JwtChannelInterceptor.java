package org.example.tears.Config;

import lombok.RequiredArgsConstructor;
import org.example.tears.Model.JwtUtil;
import org.example.tears.Model.User;
import org.example.tears.Repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        // Only authenticate CONNECT
        if (!StompCommand.CONNECT.equals(
                accessor.getCommand()
        )) {

            return message;
        }

        // =========================
        // GET AUTHORIZATION
        // =========================

        String auth =
                accessor.getFirstNativeHeader(
                        "Authorization"
                );

        if (auth == null ||
                !auth.startsWith("Bearer ")) {

            throw new IllegalArgumentException(
                    "ACCESS_TOKEN_INVALID"
            );
        }

        String token = auth.substring(7);

        try {

            // =========================
            // TOKEN TYPE
            // =========================

            String tokenType =
                    jwtUtil.getTokenType(token);

            if (!"ACCESS".equals(tokenType)) {

                throw new IllegalArgumentException(
                        "ACCESS_TOKEN_INVALID"
                );
            }

            // =========================
            // PHONE
            // =========================

            String phone =
                    jwtUtil.getPhoneFromToken(token);

            // =========================
            // ROLE
            // =========================

            String role =
                    jwtUtil.getRoleFromToken(token);

            if (phone == null ||
                    role == null) {

                throw new IllegalArgumentException(
                        "ACCESS_TOKEN_INVALID"
                );
            }

            // =========================
            // USER
            // =========================

            User user =
                    userRepository
                            .findByPhoneNumber(phone)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "ACCESS_TOKEN_INVALID"
                                    )
                            );

            // =========================
            // AUTHENTICATION
            // =========================

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            )
                    );

            accessor.setUser(authentication);

            System.out.println(
                    "WEBSOCKET CONNECT: "
                            + phone
                            + " | ROLE: "
                            + role
            );

            return message;

        }

        // =========================
        // EXPIRED
        // =========================

        catch (ExpiredJwtException e) {

            throw new IllegalArgumentException(
                    "ACCESS_TOKEN_EXPIRED"
            );
        }

        // =========================
        // INVALID
        // =========================

        catch (JwtException |
               IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "ACCESS_TOKEN_INVALID"
            );
        }
    }
}