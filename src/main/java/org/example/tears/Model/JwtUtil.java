package org.example.tears.Model;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;

    private final long accessExpirationMillis;

    private final long refreshExpirationMillis;

    public JwtUtil(
            @Value("${jwt.secret}")
            String secret,

            @Value("${jwt.access-expiration}")
            long accessExpirationMillis,

            @Value("${jwt.refresh-expiration}")
            long refreshExpirationMillis
    ) {

        this.key =
                Keys.hmacShaKeyFor(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        this.accessExpirationMillis =
                accessExpirationMillis;

        this.refreshExpirationMillis =
                refreshExpirationMillis;
    }

    // =========================
    // ACCESS TOKEN
    // =========================

    public String generateAccessToken(
            String phone,
            String role
    ) {

        return Jwts.builder()

                .setSubject(phone)

                .claim(
                        "role",
                        role
                )

                .claim(
                        "tokenType",
                        "ACCESS"
                )

                .setIssuedAt(
                        new Date()
                )

                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        + accessExpirationMillis
                        )
                )

                .signWith(key)

                .compact();
    }

    // =========================
    // REFRESH TOKEN
    // =========================

    public String generateRefreshToken(
            String phone
    ) {

        return Jwts.builder()

                .setSubject(phone)

                .claim(
                        "tokenType",
                        "REFRESH"
                )

                .setIssuedAt(
                        new Date()
                )

                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        + refreshExpirationMillis
                        )
                )

                .signWith(key)

                .compact();
    }

    // =========================
    // PARSE
    // =========================

    public Jws<Claims> parseToken(
            String token
    ) throws JwtException {

        return Jwts.parserBuilder()

                .setSigningKey(key)

                .build()

                .parseClaimsJws(token);
    }

    // =========================
    // PHONE
    // =========================

    public String getPhoneFromToken(
            String token
    ) {

        return parseToken(token)
                .getBody()
                .getSubject();
    }

    // =========================
    // ROLE
    // =========================

    public String getRoleFromToken(
            String token
    ) {

        return parseToken(token)
                .getBody()
                .get("role", String.class);
    }

    // =========================
    // TOKEN TYPE
    // =========================

    public String getTokenType(
            String token
    ) {

        return parseToken(token)
                .getBody()
                .get("tokenType", String.class);
    }

    // =========================
    // EXPIRATION
    // =========================

    public long getAccessExpirationSeconds() {

        return accessExpirationMillis / 1000;
    }

    public long getRefreshExpirationSeconds() {

        return refreshExpirationMillis / 1000;
    }
}