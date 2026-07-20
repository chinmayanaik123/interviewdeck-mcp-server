package com.interviewdeck.mcp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${interviewdeck.firebase.credentials-path:#{null}}")
    private String credentialsPath;

    @Value("${FIREBASE_CREDENTIALS_JSON:#{null}}")
    private String credentialsJson;

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initFirebase() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream credStream;

            if (credentialsJson != null && !credentialsJson.isBlank()) {
                credStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
                log.info("Using Firebase credentials from environment variable");
            } else if (credentialsPath != null && !credentialsPath.isBlank()) {
                Resource resource = resourceLoader.getResource(credentialsPath.startsWith("classpath:")
                        ? credentialsPath
                        : "file:" + credentialsPath);
                credStream = resource.getInputStream();
                log.info("Using Firebase credentials from file: {}", credentialsPath);
            } else {
                throw new IllegalStateException("No Firebase credentials configured. Set FIREBASE_CREDENTIALS_JSON env var or interviewdeck.firebase.credentials-path property.");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credStream))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully");
        }
    }
}
