package org.example.tears.InpDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.example.tears.Enums.JobTitle;

@Data
public class EmployeeRegisterDTO {

    @NotBlank
    private String fullName;

    @NotBlank
    private String phoneNumber;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private JobTitle jobTitle;

}
