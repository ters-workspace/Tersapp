package org.example.tears.Enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ServiceOption {


    FULL_MAINTENANCE("صيانة شاملة", 250),
    BODY_PAINT("سمكرة ودهان", 250),
    ELECTRONIC_CHECK("فحص إلكتروني", 150);//150

    All
    private final String displayName;
    private final int price;

}
