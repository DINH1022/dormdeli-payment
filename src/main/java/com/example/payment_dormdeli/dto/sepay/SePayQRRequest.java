package com.example.payment_dormdeli.dto.sepay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SePayQRRequest {
    private String accountNumber;
    private String accountName;
    private String bankCode;
    private BigDecimal amount;
    private String content;
}
