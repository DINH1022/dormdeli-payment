package com.example.payment_dormdeli.dto.vnpay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VNPayRequest {
    
    private String orderId;
    private BigDecimal amount;
    private String orderInfo;
    private String returnUrl;
    private String ipAddress;
}
