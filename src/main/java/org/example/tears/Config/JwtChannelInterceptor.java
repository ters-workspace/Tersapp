package org.example.tears.Config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        if (!StompCommand.CONNECT.equals(
                accessor.getCommand()
        )) {
            return message;
        }

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

            String tokenType =
                    jwtUtil.getTokenType(token);

            if (!"ACCESS".equals(tokenType)) {

                throw new IllegalArgumentException(
                        "ACCESS_TOKEN_INVALID"
                );
            }

            String phone =
                    jwtUtil.getPhoneFromToken(token);

            String role =
                    jwtUtil.getRoleFromToken(token);

            if (phone == null ||
                    role == null) {

                throw new IllegalArgumentException(
                        "ACCESS_TOKEN_INVALID"
                );
            }

            User user =
                    userRepository
                            .findByPhoneNumber(phone)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "ACCESS_TOKEN_INVALID"
                                    )
                            );

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
            String sessionId = accessor.getSessionId();

            if (sessionId != null) {
                sessionRegistry.register(sessionId, user);
            }

            System.out.println(
                    "WEBSOCKET CONNECT: "
                            + phone
                            + " | ROLE: "
                            + role
                            + " | SESSION: "
                            + sessionId
            );

            return message;

        } catch (ExpiredJwtException e) {

            throw new IllegalArgumentException(
                    "ACCESS_TOKEN_EXPIRED"
            );

        } catch (JwtException |
                 IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "ACCESS_TOKEN_INVALID"
            );
        }
    }
}