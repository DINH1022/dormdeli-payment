package com.example.payment_dormdeli.dto.sepay;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SePayTransferInfo {
    private Long id;
    private String transaction_date;
    private String account_number;
    private String code;
    private String content;
    private Double transfer_amount;
    private String reference_number;
    private String body;
    private String gate_name;
}
