package com.example.payment_dormdeli.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sepay")
@Data
public class SePayConfig {
    private String apiKey;
    private String accountNumber;
    private String accountName;
    private String bankCode;
    private String endpoint;
    private String webhookUrl;
}
