package org.example.tears.DTO;

import lombok.Data;

@Data
public class VerifyEmployeeOtpDTO {

    private String email;
    private String otp;
}