package org.example.tears.DTO;

import lombok.Data;
import org.example.tears.Enums.CancelReason;
import org.example.tears.Enums.RefundMethod;

@Data
public class CancelRequestDto {

private CancelReason reason;
private String otherReason;

private RefundMethod refundMethod;

}
