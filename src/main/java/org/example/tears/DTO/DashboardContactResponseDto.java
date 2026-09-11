package org.example.tears.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardContactResponseDto {

    private String itemType;

    private List<ContactInfoDto> contacts;
}
