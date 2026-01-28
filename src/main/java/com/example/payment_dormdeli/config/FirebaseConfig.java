package com.example.payment_dormdeli.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;
    
    private final ResourceLoader resourceLoader;
    
    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public Firestore firestore() throws IOException {
        try {
            log.info("Loading Firebase credentials from: {}", credentialsPath);
            
            // Check if path is absolute file path or classpath
            Resource resource;
            if (credentialsPath.startsWith("/") || credentialsPath.contains(":")) {
                // Absolute path or with scheme (file:, classpath:)
                resource = resourceLoader.getResource(credentialsPath.startsWith("/") ? "file:" + credentialsPath : credentialsPath);
            } else {
                // Relative path, use classpath
                resource = resourceLoader.getResource("classpath:" + credentialsPath);
            }
            
            InputStream serviceAccount = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }

            return FirestoreClient.getFirestore();
        } catch (IOException e) {
            log.error("Error initializing Firebase: {}", e.getMessage());
            throw e;
        }
    }
}
