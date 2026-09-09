package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponseDto {

    private String accessToken;

    private String refreshToken;

    private long accessTokenExpiresIn;

    private long refreshTokenExpiresIn;

    private String tokenType;
}