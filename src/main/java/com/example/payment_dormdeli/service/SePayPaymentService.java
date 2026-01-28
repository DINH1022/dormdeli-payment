package com.example.payment_dormdeli.service;

import com.example.payment_dormdeli.config.SePayConfig;
import com.example.payment_dormdeli.dto.PaymentRequest;
import com.example.payment_dormdeli.dto.PaymentResponse;
import com.example.payment_dormdeli.dto.sepay.SePayQRRequest;
import com.example.payment_dormdeli.dto.sepay.SePayTransferInfo;
import com.example.payment_dormdeli.model.Payment;
import com.example.payment_dormdeli.model.PaymentMethod;
import com.example.payment_dormdeli.model.PaymentStatus;
import com.example.payment_dormdeli.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SePayPaymentService {
    private final WebClient.Builder webClientBuilder;
    
    private final SePayConfig sePayConfig;
    private final PaymentRepository paymentRepository;
    
    /**
     * Tạo yêu cầu thanh toán SePay bằng QR code
     * SePay hoạt động qua chuyển khoản ngân hàng, không có API tạo payment URL
     * Client sẽ tạo QR code từ thông tin tài khoản ngân hàng
     */
    public PaymentResponse createPayment(PaymentRequest request) {
        try {
            // Check if order already exists
            if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
                return PaymentResponse.builder()
                        .status(PaymentStatus.FAILED)
                        .message("Order ID already exists")
                        .build();
            }
            
            // Create payment record
            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .paymentMethod(PaymentMethod.SEPAY)
                    .status(PaymentStatus.PENDING)
                    .amount(request.getAmount())
                    .orderInfo(request.getOrderInfo())
                    .userId(request.getUserId())
                    .extraData(request.getExtraData())
                    .build();
            
            paymentRepository.save(payment);
            
            // Generate QR content for bank transfer
            // Format: Bank Code + Account Number + Amount + Content
            String qrContent = generateQRContent(request);
            
            return PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .paymentUrl(qrContent) // QR content for bank transfer
                    .status(PaymentStatus.PENDING)
                    .amount(request.getAmount())
                    .message("Scan QR code to pay via bank transfer")
                    .build();
            
        } catch (Exception e) {
            log.error("Error creating SePay payment", e);
            return PaymentResponse.builder()
                    .status(PaymentStatus.FAILED)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Generate QR code content for bank transfer
     * Uses VietQR standard format
     */
    private String generateQRContent(PaymentRequest request) {
        SePayQRRequest qrRequest = SePayQRRequest.builder()
                .accountNumber(sePayConfig.getAccountNumber())
                .accountName(sePayConfig.getAccountName())
                .bankCode(sePayConfig.getBankCode())
                .amount(request.getAmount())
                .content(request.getOrderId()) // Use orderId as transfer content for tracking
                .build();
        
        // VietQR format: https://api.vietqr.io/v2/generate
        String qrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%s&addInfo=%s&accountName=%s",
                qrRequest.getBankCode(),
                qrRequest.getAccountNumber(),
                "compact",
                qrRequest.getAmount().longValue(),
                qrRequest.getContent(),
                qrRequest.getAccountName().replace(" ", "%20")
        );
        
        return qrUrl;
    }
    
    /**
     * Handle webhook from SePay when payment is received
     */
    public boolean handleWebhook(SePayTransferInfo transferInfo) {
        try {
            log.info("Received SePay webhook: {}", transferInfo);
            
            // Extract orderId from transfer content
            String orderId = extractOrderId(transferInfo.getContent());
            
            if (orderId == null) {
                log.error("Could not extract orderId from transfer content: {}", transferInfo.getContent());
                return false;
            }
            
            // Find payment
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            
            if (payment == null) {
                log.error("Payment not found for order: {}", orderId);
                return false;
            }
            
            // Check if already processed
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment already processed for order: {}", orderId);
                return true;
            }
            
            // Verify amount
            BigDecimal transferAmount = BigDecimal.valueOf(transferInfo.getTransfer_amount());
            if (transferAmount.compareTo(payment.getAmount()) < 0) {
                log.error("Transfer amount {} is less than payment amount {} for order: {}", 
                        transferAmount, payment.getAmount(), orderId);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Insufficient amount transferred");
                paymentRepository.save(payment);
                return false;
            }
            
            // Update payment
            payment.setTransactionId(transferInfo.getReference_number());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCompletedAt(new Date());
            paymentRepository.save(payment);
            
            log.info("Updated payment status for order: {} to SUCCESS", orderId);
            return true;
            
        } catch (Exception e) {
            log.error("Error handling SePay webhook", e);
            return false;
        }
    }
    
    /**
     * Extract orderId from transfer content
     * Content format should include the orderId
     */
    private String extractOrderId(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Simple extraction - assumes orderId is in the content
        // You may need to adjust this based on your actual content format
        String[] parts = content.split(" ");
        for (String part : parts) {
            if (part.matches("ORDER\\d+") || part.matches("ORD\\d+")) {
                return part;
            }
        }
        
        // If no specific pattern found, return the whole content
        return content.trim();
    }
    
    public Payment getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        
        // If payment is PENDING, check SePay for updates
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            checkPaymentFromSePay(payment);
        }
        
        return payment;
    }
    
    /**
     * Check SePay API for payment updates
     */
    private void checkPaymentFromSePay(Payment payment) {
        try {
            log.info("Checking SePay API for order: {}", payment.getOrderId());
            
            WebClient webClient = webClientBuilder.build();
            
            // Call SePay API to get recent transactions
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("my.sepay.vn")
                            .path("/userapi/transactions/list")
                            .queryParam("limit", 50)
                            .build())
                    .header("Authorization", "Bearer " + sePayConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("transactions")) {
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.get("transactions");
                
                // Find matching transaction
                for (Map<String, Object> txn : transactions) {
                    String content = (String) txn.get("transaction_content");
                    
                    if (content != null && content.contains(payment.getOrderId())) {
                        // Found matching transaction
                        Object amountObj = txn.get("amount_in");
                        long transferAmount = 0;
                        
                        if (amountObj instanceof Integer) {
                            transferAmount = ((Integer) amountObj).longValue();
                        } else if (amountObj instanceof Long) {
                            transferAmount = (Long) amountObj;
                        } else if (amountObj instanceof Double) {
                            transferAmount = ((Double) amountObj).longValue();
                        }
                        
                        BigDecimal amount = BigDecimal.valueOf(transferAmount);
                        
                        // Verify amount
                        if (amount.compareTo(payment.getAmount()) >= 0) {
                            payment.setStatus(PaymentStatus.SUCCESS);
                            payment.setTransactionId((String) txn.get("reference_number"));
                            payment.setCompletedAt(new Date());
                            paymentRepository.save(payment);
                            log.info("Payment confirmed for order: {}", payment.getOrderId());
                            break;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking SePay API: {}", e.getMessage());
        }
    }
    
    /**
     * Check payment status by querying SePay API
     * This can be called periodically to check for payments
     */
    public void checkPendingPayments() {
        // This would query SePay API to get recent transactions
        // and match them with pending payments
        log.info("Checking pending payments...");
        // Implementation depends on SePay API integration
    }
    
    /**
     * Manually confirm payment (for development/testing)
     */
    public boolean manualConfirmPayment(String orderId, String transactionId) {
        try {
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            
            if (payment == null) {
                log.error("Payment not found for order: {}", orderId);
                return false;
            }
            
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment already completed for order: {}", orderId);
                return true;
            }
            
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(transactionId != null ? transactionId : "MANUAL_" + System.currentTimeMillis());
            payment.setCompletedAt(new Date());
            paymentRepository.save(payment);
            
            log.info("Manually confirmed payment for order: {}", orderId);
            return true;
            
        } catch (Exception e) {
            log.error("Error confirming payment: {}", e.getMessage());
            return false;
        }
    }
}
