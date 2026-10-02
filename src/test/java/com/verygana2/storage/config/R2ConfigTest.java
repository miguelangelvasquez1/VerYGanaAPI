package com.verygana2.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** La URL prefirmada se firma localmente: no hace falta red ni MinIO. */
@DisplayName("R2Config - estilo de direccionamiento del bucket")
class R2ConfigTest {

    private static R2Config config(boolean pathStyle) {
        R2Config config = new R2Config();
        config.setAccessKeyId("loadtest-r2-access");
        config.setSecretAccessKey("loadtest-r2-secret");
        config.setBucketName("loadtest-campaigns");
        config.setEndpoint("http://minio:9000");
        config.setPathStyleAccess(pathStyle);
        return config;
    }

    private static URL presign(R2Config config) {
        try (S3Presigner presigner = config.r2Presigner()) {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket("loadtest-campaigns").key("ads/video.mp4").build())
                    .build()).url();
        }
    }

    @Test
    @DisplayName("por defecto el bucket va en el subdominio (virtual-hosted), como R2")
    void presignedUrlIsVirtualHostedByDefault() {
        assertThat(new R2Config().isPathStyleAccess()).isFalse();

        URL url = presign(config(false));

        assertThat(url.getHost()).isEqualTo("loadtest-campaigns.minio");
        assertThat(url.getPath()).isEqualTo("/ads/video.mp4");
    }

    @Test
    @DisplayName("con pathStyleAccess el bucket va en la ruta, como lo espera MinIO")
    void presignedUrlUsesPathStyleWhenEnabled() {
        URL url = presign(config(true));

        assertThat(url.getHost()).isEqualTo("minio");
        assertThat(url.getPort()).isEqualTo(9000);
        assertThat(url.getPath()).isEqualTo("/loadtest-campaigns/ads/video.mp4");
    }

    @Test
    @DisplayName("el S3Client también se construye con path-style")
    void clientBuildsWithPathStyle() {
        try (var client = config(true).r2Client()) {
            assertThat(client).isNotNull();
        }
    }
}
