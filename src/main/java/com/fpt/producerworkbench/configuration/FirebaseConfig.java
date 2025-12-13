package com.fpt.producerworkbench.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Configuration for Push Notifications
 * Initializes Firebase Admin SDK for sending FCM messages
 * 
 * Supports two modes:
 * 1. Environment variables (recommended for production)
 * 2. File-based (fallback, for local development)
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.config-path:firebase/serviceAccountKey.json}")
    private String firebaseConfigPath;

    // Environment variables for Firebase service account
    @Value("${FIREBASE_PROJECT_ID:}")
    private String projectId;

    @Value("${FIREBASE_PRIVATE_KEY_ID:}")
    private String privateKeyId;

    @Value("${FIREBASE_PRIVATE_KEY:}")
    private String privateKey;

    @Value("${FIREBASE_CLIENT_EMAIL:}")
    private String clientEmail;

    @Value("${FIREBASE_CLIENT_ID:}")
    private String clientId;

    @Value("${FIREBASE_CLIENT_CERT_URL:}")
    private String clientCertUrl;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = loadCredentials();
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase Admin SDK initialized successfully");
            } else {
                log.info("✅ Firebase Admin SDK already initialized");
            }
        } catch (IOException e) {
            log.error("❌ Failed to initialize Firebase Admin SDK: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }

    /**
     * Load credentials from environment variables or fall back to file
     */
    private GoogleCredentials loadCredentials() throws IOException {
        // Check if env vars are configured
        if (StringUtils.hasText(projectId) && StringUtils.hasText(privateKey) && StringUtils.hasText(clientEmail)) {
            log.info("📝 Loading Firebase credentials from environment variables");
            return loadFromEnvironment();
        }
        
        // Fall back to file
        log.info("📁 Loading Firebase credentials from file: {}", firebaseConfigPath);
        return loadFromFile();
    }

    /**
     * Build GoogleCredentials from environment variables
     */
    private GoogleCredentials loadFromEnvironment() throws IOException {
        // Firebase private key comes with escaped newlines, need to convert them
        String formattedPrivateKey = privateKey.replace("\\n", "\n");
        
        // Build JSON structure
        String jsonCredentials = String.format("""
                {
                  "type": "service_account",
                  "project_id": "%s",
                  "private_key_id": "%s",
                  "private_key": "%s",
                  "client_email": "%s",
                  "client_id": "%s",
                  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                  "token_uri": "https://oauth2.googleapis.com/token",
                  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                  "client_x509_cert_url": "%s",
                  "universe_domain": "googleapis.com"
                }
                """,
                projectId,
                privateKeyId,
                formattedPrivateKey,
                clientEmail,
                clientId,
                clientCertUrl
        );

        InputStream credentialsStream = new ByteArrayInputStream(jsonCredentials.getBytes(StandardCharsets.UTF_8));
        return GoogleCredentials.fromStream(credentialsStream);
    }

    /**
     * Load credentials from classpath file
     */
    private GoogleCredentials loadFromFile() throws IOException {
        ClassPathResource resource = new ClassPathResource(firebaseConfigPath);
        InputStream serviceAccount = resource.getInputStream();
        return GoogleCredentials.fromStream(serviceAccount);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}
