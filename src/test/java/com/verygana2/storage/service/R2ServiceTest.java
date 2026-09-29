package com.verygana2.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.exceptions.StorageException;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.storage.config.R2Config;

import jakarta.validation.ValidationException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
@DisplayName("R2Service")
class R2ServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock private S3Client r2Client;
    @Mock private S3Presigner r2Presigner;

    private R2Service service;

    @BeforeEach
    void setUp() {
        R2Config config = new R2Config();
        config.setBucketName(BUCKET);

        service = new R2Service();
        ReflectionTestUtils.setField(service, "r2Client", r2Client);
        ReflectionTestUtils.setField(service, "r2Presigner", r2Presigner);
        ReflectionTestUtils.setField(service, "r2Config", config);
    }

    private static final Set<SupportedMimeType> IMAGES = Set.of(
            SupportedMimeType.IMAGE_PNG, SupportedMimeType.IMAGE_JPEG, SupportedMimeType.IMAGE_WEBP);

    private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };

    /** El objeto subido: content-type declarado (el que R2 guardó) y los bytes reales. */
    private void givenUploadedObject(String declaredContentType, byte[] realBytes) {
        when(r2Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength((long) realBytes.length)
                        .contentType(declaredContentType)
                        .build());
        // detectRealMimeType lee los primeros bytes del objeto.
        lenient().when(r2Client.getObject(any(GetObjectRequest.class))).thenAnswer(inv ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(realBytes))));
    }

    private DeleteObjectRequest capturedDelete() {
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(r2Client).deleteObject(captor.capture());
        return captor.getValue();
    }

    // ─── validateUploadedObject ─────────────────────────────────────────────

    @Nested
    @DisplayName("validateUploadedObject")
    class ValidateUploadedObject {

        @Test
        @DisplayName("contenido real de un tipo que no está en SupportedMimeType (declarado image/png): borra el objeto y lanza ValidationException")
        void unsupportedRealType_deletesObject() {
            byte[] notAnImage = "esto es texto plano, no una imagen".getBytes(StandardCharsets.UTF_8);
            givenUploadedObject("image/png", notAnImage);

            assertThatThrownBy(() -> service.validateUploadedObject(
                    false, "raffles/x.png", notAnImage.length, 10_000, IMAGES))
                    .isInstanceOf(ValidationException.class);

            // Antes: SupportedMimeType.fromValue lanzaba ANTES de llegar al delete y el objeto
            // quedaba en public/ accesible por URL.
            DeleteObjectRequest delete = capturedDelete();
            assertThat(delete.bucket()).isEqualTo(BUCKET);
            assertThat(delete.key()).isEqualTo("public/raffles/x.png");
        }

        @Test
        @DisplayName("content-type almacenado fuera del enum: borra el objeto y lanza ValidationException")
        void unsupportedDeclaredType_deletesObject() {
            byte[] bytes = "MZ".getBytes(StandardCharsets.UTF_8);
            givenUploadedObject("application/x-msdownload", bytes);

            assertThatThrownBy(() -> service.validateUploadedObject(
                    true, "ads/a.exe", bytes.length, 10_000, IMAGES))
                    .isInstanceOf(ValidationException.class);

            assertThat(capturedDelete().key()).isEqualTo("private/ads/a.exe");
        }

        @Test
        @DisplayName("tipo real soportado pero no permitido para este flujo (PDF donde solo se aceptan imágenes): borra el objeto")
        void supportedButNotAllowedRealType_deletesObject() {
            byte[] pdf = "%PDF-1.4\n%fake".getBytes(StandardCharsets.UTF_8);
            givenUploadedObject("image/png", pdf);

            assertThatThrownBy(() -> service.validateUploadedObject(
                    false, "categories/c.png", pdf.length, 10_000, IMAGES))
                    .isInstanceOf(ValidationException.class);

            assertThat(capturedDelete().key()).isEqualTo("public/categories/c.png");
        }

        @Test
        @DisplayName("imagen válida: devuelve el mime y NO borra nada")
        void validImage_isKept() {
            givenUploadedObject("image/png", PNG_MAGIC);

            SupportedMimeType mime = service.validateUploadedObject(
                    false, "raffles/ok.png", PNG_MAGIC.length, 10_000, IMAGES);

            assertThat(mime).isEqualTo(SupportedMimeType.IMAGE_PNG);
            verify(r2Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    // ─── deleteObjects / deletePrivateObjects ───────────────────────────────

    @Nested
    @DisplayName("borrado en batch")
    class BatchDelete {

        @Test
        @DisplayName("deleteObjects: si R2 reporta objetos que no se pudieron borrar, lanza StorageException (el llamador no debe dar el borrado por hecho)")
        void partialFailure_throws() {
            when(r2Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(
                    DeleteObjectsResponse.builder()
                            .errors(S3Error.builder().key("private/a").code("InternalError").message("boom").build())
                            .build());

            assertThatThrownBy(() -> service.deleteObjects(List.of("private/a", "private/b")))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("deleteObjects: sin errores no lanza")
        void success_doesNotThrow() {
            when(r2Client.deleteObjects(any(DeleteObjectsRequest.class)))
                    .thenReturn(DeleteObjectsResponse.builder().build());

            assertThatCode(() -> service.deleteObjects(List.of("private/a"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deletePrivateObjects: recibe las keys como se guardan en las filas y les añade el prefijo private/")
        void deletePrivateObjects_addsPrefix() {
            when(r2Client.deleteObjects(any(DeleteObjectsRequest.class)))
                    .thenReturn(DeleteObjectsResponse.builder().build());

            service.deletePrivateObjects(List.of("branding/1/resources/a.png", "branding/1/resources/b.png"));

            ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
            verify(r2Client).deleteObjects(captor.capture());
            assertThat(captor.getValue().delete().objects())
                    .extracting(o -> o.key())
                    .containsExactly("private/branding/1/resources/a.png", "private/branding/1/resources/b.png");
        }

        @Test
        @DisplayName("deletePrivateObjects: lista vacía o nula no llama a R2")
        void emptyList_isNoop() {
            service.deletePrivateObjects(List.of());
            service.deletePrivateObjects(null);

            verify(r2Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
        }
    }
}
