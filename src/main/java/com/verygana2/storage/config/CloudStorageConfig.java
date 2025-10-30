package com.verygana2.storage.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuración de almacenamiento en la nube
 * Soporta múltiples providers mediante properties
 */
@Configuration
public class CloudStorageConfig {

    /**
     * Configuración para Cloudinary
     * Se activa cuando storage.provider=cloudinary
     */
    @Configuration
    @ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary")
    static class CloudinaryConfig {

        @Value("${cloudinary.cloud-name}")
        private String cloudName;

        @Value("${cloudinary.api-key}")
        private String apiKey;

        @Value("${cloudinary.api-secret}")
        private String apiSecret;

        @Bean
        public Cloudinary cloudinary() {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("api_key", apiKey);
            config.put("api_secret", apiSecret);
            config.put("secure", "true");
            
            return new Cloudinary(config);
        }
    }

    /**
     * Configuración para Cloudflare R2
     * Se activa cuando storage.provider=r2
     */
    @Configuration
    @ConditionalOnProperty(name = "storage.provider", havingValue = "r2")
    static class R2Config {

        @Value("${cloudflare.r2.account-id}")
        private String accountId;

        @Value("${cloudflare.r2.access-key-id}")
        private String accessKeyId;

        @Value("${cloudflare.r2.secret-access-key}")
        private String secretAccessKey;

        @Bean
        public S3Client r2Client() {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId,
                secretAccessKey
            );

            return S3Client.builder()
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(
                    String.format("https://%s.r2.cloudflarestorage.com", accountId)
                ))
                .build();
        }

        @Bean
        public S3Presigner r2Presigner() {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId,
                secretAccessKey
            );

            return S3Presigner.builder()
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(
                    String.format("https://%s.r2.cloudflarestorage.com", accountId)
                ))
                .build();
        }
    }
}
