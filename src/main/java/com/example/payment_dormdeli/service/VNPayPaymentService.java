package com.example.payment_dormdeli.service;

import com.example.payment_dormdeli.config.VNPayConfig;
import com.example.payment_dormdeli.dto.PaymentRequest;
import com.example.payment_dormdeli.dto.PaymentResponse;
import com.example.payment_dormdeli.dto.vnpay.VNPayResponse;
import com.example.payment_dormdeli.model.Payment;
import com.example.payment_dormdeli.model.PaymentMethod;
import com.example.payment_dormdeli.model.PaymentStatus;
import com.example.payment_dormdeli.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayPaymentService {
    
    private final VNPayConfig vnPayConfig;
    private final PaymentRepository paymentRepository;
    
    /**
     * Create VNPay payment URL
     */
    public PaymentResponse createPayment(PaymentRequest request, String ipAddress) {
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
                    .paymentMethod(PaymentMethod.VNPAY)
                    .status(PaymentStatus.PENDING)
                    .amount(request.getAmount())
                    .orderInfo(request.getOrderInfo())
                    .userId(request.getUserId())
                    .extraData(request.getExtraData())
                    .build();
            
            paymentRepository.save(payment);
            
            // Build VNPay payment URL
            String paymentUrl = buildPaymentUrl(request, ipAddress);
            
            payment.setPaymentUrl(paymentUrl);
            paymentRepository.save(payment);
            
            return PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .paymentUrl(paymentUrl)
                    .status(PaymentStatus.PENDING)
                    .amount(request.getAmount())
                    .message("VNPay payment URL created successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error creating VNPay payment", e);
            return PaymentResponse.builder()
                    .status(PaymentStatus.FAILED)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Build VNPay payment URL
     */
    private String buildPaymentUrl(PaymentRequest request, String ipAddress) throws UnsupportedEncodingException {
        log.info("Building VNPay URL with return URL: {}", vnPayConfig.getReturnUrl());
        
        Map<String, String> vnpParams = new HashMap<>();
        
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(request.getAmount().multiply(new java.math.BigDecimal(100)).longValue()));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", request.getOrderId());
        vnpParams.put("vnp_OrderInfo", request.getOrderInfo());
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", ipAddress);
        
        // Create date with Vietnam timezone
        TimeZone vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar cld = Calendar.getInstance(vnTimeZone);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(vnTimeZone); // IMPORTANT: Set timezone for formatter
        
        String vnpCreateDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        
        log.info("VNPay CreateDate: {} (Vietnam timezone)", vnpCreateDate);
        
        // Expire after 15 minutes
        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);
        
        // Sort parameters
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        
        // Build hash data and query
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                
                // Build query string
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        
        String queryUrl = query.toString();
        String vnpSecureHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        
        return vnPayConfig.getPayUrl() + "?" + queryUrl;
    }
    
    /**
     * Verify VNPay callback
     */
    public boolean verifyCallback(Map<String, String> params) {
        try {
            String vnpSecureHash = params.get("vnp_SecureHash");
            params.remove("vnp_SecureHash");
            params.remove("vnp_SecureHashType");
            
            // Sort parameters
            List<String> fieldNames = new ArrayList<>(params.keySet());
            Collections.sort(fieldNames);
            
            StringBuilder hashData = new StringBuilder();
            Iterator<String> itr = fieldNames.iterator();
            
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = params.get(fieldName);
                
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    
                    if (itr.hasNext()) {
                        hashData.append('&');
                    }
                }
            }
            
            String signValue = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
            
            return signValue.equals(vnpSecureHash);
            
        } catch (Exception e) {
            log.error("Error verifying VNPay callback", e);
            return false;
        }
    }
    
    /**
     * Handle VNPay callback/return URL
     */
    public boolean handleCallback(Map<String, String> params) {
        try {
            // Verify signature
            if (!verifyCallback(params)) {
                log.error("Invalid VNPay signature");
                return false;
            }
            
            String orderId = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String transactionId = params.get("vnp_TransactionNo");
            
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
            
            // Update payment based on response code
            if ("00".equals(responseCode)) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setTransactionId(transactionId);
                payment.setCompletedAt(new Date());
                log.info("Payment successful for order: {}", orderId);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("VNPay response code: " + responseCode);
                log.info("Payment failed for order: {} with code: {}", orderId, responseCode);
            }
            
            paymentRepository.save(payment);
            return true;
            
        } catch (Exception e) {
            log.error("Error handling VNPay callback", e);
            return false;
        }
    }
    
    /**
     * HMAC SHA512
     */
    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            log.error("Error generating HMAC SHA512", e);
            return "";
        }
    }
    
    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId).orElse(null);
    }
}
