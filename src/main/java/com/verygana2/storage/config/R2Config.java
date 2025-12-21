package com.verygana2.storage.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "cloudflare.r2")
@Data
@Slf4j
public class R2Config {

    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucketName;
    private String cdnDomain; // Opcional: tu dominio CDN custom
    private String endpoint;

    @Bean
    public S3Client r2Client() {
        log.info("Inicializando R2 Client para bucket: {}", bucketName);

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId,
                secretAccessKey
            );

            // Endpoint de R2
            String r2Endpoint = endpoint != null && !endpoint.isEmpty()
                ? endpoint
                : String.format("https://%s.r2.cloudflarestorage.com", accountId);

            S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(false) // R2 usa virtual-hosted style
                .build();

            S3Client client = S3Client.builder()
                .region(Region.of("auto")) // R2 usa "auto"
                .endpointOverride(URI.create(r2Endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .overrideConfiguration(config -> config
                    .apiCallTimeout(Duration.ofSeconds(30))
                    .apiCallAttemptTimeout(Duration.ofSeconds(10))
                )
                .build();

            log.info("R2 Client inicializado exitosamente");
            return client;

        } catch (Exception e) {
            log.error("Error inicializando R2 Client: {}", e.getMessage(), e);
            throw new RuntimeException("Fallo al inicializar R2 Client", e);
        }
    }

    @Bean
    public S3Presigner r2Presigner() {
        log.info("Inicializando R2 Presigner");

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId,
                secretAccessKey
            );

            String r2Endpoint = endpoint != null && !endpoint.isEmpty()
                ? endpoint
                : String.format("https://%s.r2.cloudflarestorage.com", accountId);

            S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(r2Endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

            log.info("R2 Presigner inicializado exitosamente");
            return presigner;

        } catch (Exception e) {
            log.error("Error inicializando R2 Presigner: {}", e.getMessage(), e);
            throw new RuntimeException("Fallo al inicializar R2 Presigner", e);
        }
    }
}