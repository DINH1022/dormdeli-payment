package com.example.payment_dormdeli.dto.sepay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SePayCheckoutResponse {
    
    private String status;
    private String message;
    private String checkoutUrl;
    private String sessionId;
}
