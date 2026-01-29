package com.example.payment_dormdeli.controller;

import com.example.payment_dormdeli.dto.PaymentRequest;
import com.example.payment_dormdeli.dto.PaymentResponse;
import com.example.payment_dormdeli.dto.sepay.SePayTransferInfo;
import com.example.payment_dormdeli.model.Payment;
import com.example.payment_dormdeli.model.PaymentStatus;
import com.example.payment_dormdeli.service.SePayPaymentService;
import com.example.payment_dormdeli.service.VNPayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // For development - configure properly for production
public class PaymentController {
    
    private final SePayPaymentService sePayPaymentService;
    private final VNPayPaymentService vnPayPaymentService;
    
    /**
     * Create SePay payment (QR code)
     * POST /api/payment/create
     */
    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createSePayPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Creating SePay payment for order: {}", request.getOrderId());
        PaymentResponse response = sePayPaymentService.createPayment(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * SePay webhook endpoint
     * POST /api/payment/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleSePayWebhook(@RequestBody SePayTransferInfo transferInfo) {
        log.info("=== WEBHOOK RECEIVED ===");
        log.info("Full webhook data: {}", transferInfo);
        log.info("Received SePay webhook for transfer: {}", transferInfo.getReference_number());
        
        boolean success = sePayPaymentService.handleWebhook(transferInfo);
        
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("status", "success");
            response.put("message", "Payment processed successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Failed to process payment");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    /**
     * Get payment status
     * GET /api/payment/status/{orderId}
     * Optional: ?autoConfirm=true to auto-confirm if pending
     */
    @GetMapping("/status/{orderId}")
    public ResponseEntity<Payment> getPaymentStatus(
            @PathVariable String orderId,
            @RequestParam(required = false, defaultValue = "false") boolean autoConfirm) {
        log.info("Getting payment status for order: {} (autoConfirm: {})", orderId, autoConfirm);
        
        Payment payment = sePayPaymentService.getPaymentByOrderId(orderId);
        
        if (payment != null) {
            // Auto-confirm if requested and payment is still pending
            if (autoConfirm && payment.getStatus() == PaymentStatus.PENDING) {
                log.info("Auto-confirming payment for order: {}", orderId);
                sePayPaymentService.manualConfirmPayment(orderId, null);
                payment = sePayPaymentService.getPaymentByOrderId(orderId);
            }
            return ResponseEntity.ok(payment);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/payment/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Payment Service");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Manually confirm payment (for development/testing)
     * POST /api/payment/confirm/{orderId}
     */
    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @PathVariable String orderId,
            @RequestParam(required = false) String transactionId) {
        log.info("Manually confirming payment for order: {}", orderId);
        
        boolean success = sePayPaymentService.manualConfirmPayment(orderId, transactionId);
        
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("status", "success");
            response.put("message", "Payment confirmed successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Payment not found or already completed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    // ==================== VNPay Endpoints ====================
    
    /**
     * Create VNPay payment
     * POST /api/payment/vnpay/create
     */
    @PostMapping("/vnpay/create")
    public ResponseEntity<PaymentResponse> createVNPayPayment(
            @Valid @RequestBody PaymentRequest request,
            HttpServletRequest httpRequest) {
        log.info("Creating VNPay payment for order: {}", request.getOrderId());
        
        String ipAddress = getClientIpAddress(httpRequest);
        PaymentResponse response = vnPayPaymentService.createPayment(request, ipAddress);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * VNPay return URL handler
     * GET /api/payment/vnpay/return
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Map<String, Object>> handleVNPayReturn(@RequestParam Map<String, String> params) {
        log.info("Received VNPay return callback");
        
        boolean success = vnPayPaymentService.handleCallback(params);
        String orderId = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("success", success && "00".equals(responseCode));
        response.put("responseCode", responseCode);
        
        if (success && "00".equals(responseCode)) {
            response.put("message", "Payment successful");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Payment failed or invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    /**
     * VNPay IPN (Instant Payment Notification) handler
     * GET /api/payment/vnpay/ipn
     */
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, Object>> handleVNPayIPN(@RequestParam Map<String, String> params) {
        log.info("Received VNPay IPN callback");
        
        boolean success = vnPayPaymentService.handleCallback(params);
        String responseCode = params.get("vnp_ResponseCode");
        
        Map<String, Object> response = new HashMap<>();
        
        if (success && "00".equals(responseCode)) {
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
        } else {
            response.put("RspCode", "99");
            response.put("Message", "Confirm Fail");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        
        // Handle multiple IPs (take first one - real client IP)
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        
        String finalIp = ipAddress != null ? ipAddress : "127.0.0.1";
        log.debug("Client IP address: {}", finalIp);
        
        return finalIp;
    }
}
