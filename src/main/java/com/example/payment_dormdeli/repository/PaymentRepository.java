package com.example.payment_dormdeli.repository;

import com.example.payment_dormdeli.model.Payment;
import com.example.payment_dormdeli.model.PaymentStatus;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
@Slf4j
public class PaymentRepository {
    
    private final Firestore firestore;
    private static final String COLLECTION_NAME = "payments";
    
    public Payment save(Payment payment) {
        try {
            if (payment.getId() == null || payment.getId().isEmpty()) {
                payment.setId(UUID.randomUUID().toString());
                payment.setCreatedAt(new Date());
            }
            payment.setUpdatedAt(new Date());
            
            ApiFuture<WriteResult> future = firestore.collection(COLLECTION_NAME)
                    .document(payment.getId())
                    .set(payment);
            
            future.get();
            log.info("Payment saved with ID: {}", payment.getId());
            return payment;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving payment: {}", e.getMessage());
            throw new RuntimeException("Error saving payment", e);
        }
    }
    
    public Optional<Payment> findById(String id) {
        try {
            DocumentSnapshot document = firestore.collection(COLLECTION_NAME)
                    .document(id)
                    .get()
                    .get();
            
            if (document.exists()) {
                Payment payment = document.toObject(Payment.class);
                if (payment != null) {
                    payment.setId(document.getId());
                }
                return Optional.ofNullable(payment);
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment by ID: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    public Optional<Payment> findByOrderId(String orderId) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("orderId", orderId)
                    .limit(1);
            
            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            
            if (!documents.isEmpty()) {
                Payment payment = documents.get(0).toObject(Payment.class);
                payment.setId(documents.get(0).getId());
                return Optional.of(payment);
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment by orderId: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    public Optional<Payment> findByTransactionId(String transactionId) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("transactionId", transactionId)
                    .limit(1);
            
            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            
            if (!documents.isEmpty()) {
                Payment payment = documents.get(0).toObject(Payment.class);
                payment.setId(documents.get(0).getId());
                return Optional.of(payment);
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment by transactionId: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    public List<Payment> findByUserId(String userId) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId);
            
            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            List<Payment> payments = new ArrayList<>();
            
            for (QueryDocumentSnapshot document : documents) {
                Payment payment = document.toObject(Payment.class);
                payment.setId(document.getId());
                payments.add(payment);
            }
            
            return payments;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payments by userId: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Payment> findByStatus(PaymentStatus status) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("status", status.name());
            
            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            List<Payment> payments = new ArrayList<>();
            
            for (QueryDocumentSnapshot document : documents) {
                Payment payment = document.toObject(Payment.class);
                payment.setId(document.getId());
                payments.add(payment);
            }
            
            return payments;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payments by status: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Payment> findByUserIdAndStatus(String userId, PaymentStatus status) {
        try {
            Query query = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", status.name());
            
            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            List<Payment> payments = new ArrayList<>();
            
            for (QueryDocumentSnapshot document : documents) {
                Payment payment = document.toObject(Payment.class);
                payment.setId(document.getId());
                payments.add(payment);
            }
            
            return payments;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payments by userId and status: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Payment> findAll() {
        try {
            List<QueryDocumentSnapshot> documents = firestore.collection(COLLECTION_NAME)
                    .get()
                    .get()
                    .getDocuments();
            
            List<Payment> payments = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                Payment payment = document.toObject(Payment.class);
                payment.setId(document.getId());
                payments.add(payment);
            }
            
            return payments;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding all payments: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public void deleteById(String id) {
        try {
            firestore.collection(COLLECTION_NAME)
                    .document(id)
                    .delete()
                    .get();
            log.info("Payment deleted with ID: {}", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting payment: {}", e.getMessage());
            throw new RuntimeException("Error deleting payment", e);
        }
    }
}
