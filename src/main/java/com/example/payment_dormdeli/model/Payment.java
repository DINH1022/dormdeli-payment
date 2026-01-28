package com.example.payment_dormdeli.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    
    private String id;
    
    private String orderId;
    
    private String transactionId;
    
    private PaymentMethod paymentMethod;
    
    private PaymentStatus status;
    
    private BigDecimal amount;
    
    private String orderInfo;
    
    private String userId;
    
    private String extraData;
    
    private String paymentUrl;
    
    private String errorMessage;
    
    private Date createdAt;
    
    private Date updatedAt;
    
    private Date completedAt;
}
