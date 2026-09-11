package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactInfoDto {

    private String type;

    private String name;

    private String phone;

    private String email;

    private String role;
}