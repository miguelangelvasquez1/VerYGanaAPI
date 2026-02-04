package com.verygana2.storage.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.exceptions.StorageException;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.storage.config.R2Config;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Slf4j
public class R2Service {

    private static final Duration DEFAULT_UPLOAD_EXPIRATION = Duration.ofMinutes(5);

    @Autowired
    private S3Client r2Client;

    @Autowired
    private S3Presigner r2Presigner;

    @Autowired
    private R2Config r2Config;

    private static final String PRIVATE_PREFIX = "private/";

    /**
     * Genera URL pre-firmada para subir un objeto desde el cliente.
     * @param objectKey clave única del objeto dentro del bucket R2.
     * @param contentType tipo de mime declarado al subir el archivo.
     * @param expirationSeconds tiempo de expiración de la URL pre-firmada.
     * 
     * @return {@link AssetUploadPermissionDTO} que contiene:
     *  <ul>
     *      <li>URL pre-firmada para subir el archivo</li>
     *      <li>URL pública o de acceso al objeto</li>
     *      <li>Tiempo de expiración en segundos</li>
     *  </ul>
     */
    public FileUploadPermissionDTO generateUploadUrl(String objectKey, String contentType) {
        
        try {
            // Validar parámetros
            validateObjectKey(objectKey); //Poner loggers audit, evitar sobreescritura?
            String privateKey = PRIVATE_PREFIX + objectKey;
            
            // Crear request de pre-signed URL
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(privateKey)
                .contentType(contentType)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(DEFAULT_UPLOAD_EXPIRATION)
                .putObjectRequest(putRequest)
                .build();

            // Generar URL firmada
            PresignedPutObjectRequest presignedRequest = 
                r2Presigner.presignPutObject(presignRequest);

            String uploadUrl = presignedRequest.url().toString();

            log.info("Pre-signed URL generada para: {}", objectKey);

            return new FileUploadPermissionDTO(uploadUrl, DEFAULT_UPLOAD_EXPIRATION.getSeconds());

        } catch (Exception e) {
            log.error("Error generando pre-signed URL para {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error generando URL de subida", e);
        }
    }

    /**
     * Validación de Objetos Post-Upload (existencia, size y conten-type)
     * @param objectKey clave única del objeto dentro del bucket R2.
     * @param expectedSizeBytes tamaño esperado del archivo.
     * @param maxSizeBytes tamaño máximo esperado del archivo según políticas.
     * @param allowedMimeTypes tipos esperados.
     */
    public SupportedMimeType validateUploadedObject(String objectKey, long expectedSizeBytes, long maxSizeBytes, Set<SupportedMimeType> allowedMimeTypes) {
        try {
            String privateKey = PRIVATE_PREFIX + objectKey;
            HeadObjectResponse head = r2Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(privateKey)
                    .build()
            );

            long realSize = head.contentLength();
            SupportedMimeType detectedMime = SupportedMimeType.fromValue(head.contentType());

            // 1. Validar tamaño máximo absoluto (política)
            if (realSize > maxSizeBytes) {
                deleteObject(privateKey);
                throw new ValidationException(
                    "Archivo excede tamaño máximo permitido. Máximo: " + maxSizeBytes + ", real: " + realSize
                );
            }

            // 2. Validar tamaño exacto contra lo declarado
            if (realSize != expectedSizeBytes) {
                deleteObject(privateKey);
                throw new ValidationException(
                    "Tamaño inválido. Esperado: " + expectedSizeBytes + ", real: " + realSize
                );
            }

            // 3. Validar content-type almacenado en R2 contra definidos (política)
            if (detectedMime == null || !allowedMimeTypes.contains(detectedMime)) {
                log.info("realContent: " + detectedMime + ", allowedMime: " + allowedMimeTypes);
                deleteObject(privateKey);
                throw new ValidationException(
                    "Content-Type inválido: " + detectedMime
                );
            }

            // 4. Validar content-type REAL (fuente de verdad)
            SupportedMimeType detectedRealMime = SupportedMimeType.fromValue(detectRealMimeType(privateKey));
            if (!allowedMimeTypes.contains(detectedRealMime)) {
                deleteObject(privateKey);
                throw new ValidationException(
                    "Content-Type real inválido: " + detectedRealMime
                );
            }

            log.info("Objeto {} validado correctamente", objectKey);
            return detectedMime;

        } catch (NoSuchKeyException e) {
            throw new ValidationException("El objeto no existe en storage");
        } catch (S3Exception e) {
            log.error("Error validando objeto {}: {}", objectKey, e.awsErrorDetails().errorMessage());
            throw new StorageException("Error accediendo a storage", e);
        }
    }

    /**
     * Verifica si un objeto existe en R2
     */
    public boolean objectExists(String objectKey) {
        try {
            r2Client.headObject(
            HeadObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(objectKey)
                .build()
            );
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error verificando existencia de {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error verificando objeto", e);
        }
    }

    /**
     * Obtiene metadata de un objeto
     */
    public ObjectMetadata getObjectMetadata(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(objectKey)
                .build();

            HeadObjectResponse response = r2Client.headObject(headRequest);

            return ObjectMetadata.builder()
                .key(objectKey)
                .contentType(response.contentType())
                .contentLength(response.contentLength())
                .lastModified(response.lastModified())
                .metadata(response.metadata())
                .etag(response.eTag())
                .build();

        } catch (NoSuchKeyException e) {
            throw new StorageException("Objeto no encontrado: " + objectKey);
        } catch (Exception e) {
            log.error("Error obteniendo metadata de {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error obteniendo metadata", e);
        }
    }

    /**
     * Elimina un objeto de R2
     */
    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(objectKey)
                .build();

            r2Client.deleteObject(deleteRequest);
            log.info("Objeto eliminado: {}", objectKey);

        } catch (Exception e) {
            log.error("Error eliminando {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error eliminando objeto", e);
        }
    }

    /**
     * Elimina múltiples objetos (batch delete)
     */
    public void deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        try {
            // R2/S3 permite hasta 1000 objetos por request
            List<List<String>> batches = partition(objectKeys, 1000);

            for (List<String> batch : batches) {
                List<ObjectIdentifier> identifiers = batch.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

                Delete delete = Delete.builder()
                    .objects(identifiers)
                    .quiet(false)
                    .build();

                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .delete(delete)
                    .build();

                DeleteObjectsResponse response = r2Client.deleteObjects(deleteRequest);

                if (response.hasErrors()) {
                    log.warn("Algunos objetos no se pudieron eliminar: {}", 
                        response.errors());
                }

                log.info("Batch delete completado: {} objetos", batch.size());
            }

        } catch (Exception e) {
            log.error("Error en batch delete: {}", e.getMessage());
            throw new StorageException("Error eliminando objetos", e);
        }
    }

    /**
     * Marca objetos como huérfanos para limpieza posterior
     * Se usa cuando la campaña no se crea después del upload
     */
    public void markAsOrphan(String objectKey) {
        try {
            // Copiar objeto con metadata actualizada
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(r2Config.getBucketName())
                .sourceKey(objectKey)
                .destinationBucket(r2Config.getBucketName())
                .destinationKey(objectKey)
                .metadata(java.util.Map.of(
                    "status", "orphan",
                    "marked-at", Instant.now().toString()
                ))
                .metadataDirective(MetadataDirective.REPLACE)
                .build();

            r2Client.copyObject(copyRequest);
            log.info("Objeto marcado como huérfano: {}", objectKey);

        } catch (Exception e) {
            log.error("Error marcando objeto como huérfano {}: {}", objectKey, e.getMessage());
            // No lanzar excepción, es operación no crítica
        }
    }

    /**
     * Limpia objetos huérfanos (ejecutar periódicamente)
     */
    public int cleanOrphanedObjects(int maxAgeHours) {
        int deletedCount = 0;
        
        try {
            Instant cutoffTime = Instant.now().minus(Duration.ofHours(maxAgeHours));

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(r2Config.getBucketName())
                .prefix("campaigns/")
                .build();

            ListObjectsV2Response listResponse;
            List<String> toDelete = new ArrayList<>();

            do {
                listResponse = r2Client.listObjectsV2(listRequest);

                for (S3Object obj : listResponse.contents()) {
                    // Verificar metadata del objeto
                    try {
                        ObjectMetadata metadata = getObjectMetadata(obj.key());
                        
                        String status = metadata.getMetadata().get("status");
                        if ("orphan".equals(status) && obj.lastModified().isBefore(cutoffTime)) {
                            toDelete.add(obj.key());
                        }
                    } catch (Exception e) {
                        log.debug("Skipping object {}: {}", obj.key(), e.getMessage());
                    }
                }

                listRequest = listRequest.toBuilder()
                    .continuationToken(listResponse.nextContinuationToken())
                    .build();

            } while (listResponse.isTruncated());

            // Eliminar objetos huérfanos
            if (!toDelete.isEmpty()) {
                deleteObjects(toDelete);
                deletedCount = toDelete.size();
                log.info("Limpieza completada: {} objetos huérfanos eliminados", deletedCount);
            }

        } catch (Exception e) {
            log.error("Error durante limpieza de huérfanos: {}", e.getMessage());
        }

        return deletedCount;
    }

    /**
     * Construye URL pública del CDN
     */
    public String buildPublicUrl(String objectKey) {

        // CDN custom (PRODUCCIÓN)
        if (r2Config.getCdnDomain() != null && !r2Config.getCdnDomain().isBlank()) {
            return String.format("https://%s/%s",
                r2Config.getCdnDomain(),
                objectKey
            );
        }

        // Fallback controlado (DEV / STAGING)
        return String.format("https://%s.r2.dev/%s",
            r2Config.getAccountId(),
            objectKey
        );
    }

    /**
     * Verifica salud del servicio R2
     */
    public boolean healthCheck() {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(r2Config.getBucketName())
                .build();

            r2Client.headBucket(headRequest);
            return true;

        } catch (Exception e) {
            log.error("R2 health check falló: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Copia objeto a otra ubicación (útil para backups)
     */
    public String copyObject(String sourceKey, String destinationKey) {
        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(r2Config.getBucketName())
                .sourceKey(sourceKey)
                .destinationBucket(r2Config.getBucketName())
                .destinationKey(destinationKey)
                .build();

            r2Client.copyObject(copyRequest);
            log.info("Objeto copiado: {} -> {}", sourceKey, destinationKey);
            
            return buildPublicUrl(destinationKey);

        } catch (Exception e) {
            log.error("Error copiando objeto: {}", e.getMessage());
            throw new StorageException("Error copiando objeto", e);
        }
    }

    /**
     * Genera URL pre-firmada para hacer GET de un objeto
     */
    public String generatePresignedUrl(String objectKey, int expiresInSeconds) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(r2Config.getBucketName())
            .key(objectKey)
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expiresInSeconds))
            .getObjectRequest(getObjectRequest)
            .build();

        PresignedGetObjectRequest presignedRequest = r2Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Valida que la key del objeto sea segura
     */
    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Object key no puede estar vacío");
        }

        if (objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new IllegalArgumentException("Object key contiene path inseguro");
        }

        if (objectKey.length() > 1024) {
            throw new IllegalArgumentException("Object key demasiado largo");
        }
    }

    /**
     * Particiona lista en sublistas de tamaño específico
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public String detectRealMimeType(String objectKey) {
        try (ResponseInputStream<GetObjectResponse> objectStream =
                r2Client.getObject(
                    GetObjectRequest.builder()
                        .bucket(r2Config.getBucketName())
                        .key(objectKey)
                        .range("bytes=0-4096")
                        .build()
                )) {

            Tika tika = new Tika();
            return tika.detect(objectStream);

        } catch (Exception e) {
            log.error("Error detectando MIME real de {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error detectando tipo real", e);
        }
    }

    // ==================== DTOs ====================

    @lombok.Data
    @lombok.Builder
    public static class ObjectMetadata {
        private String key;
        private String contentType;
        private Long contentLength;
        private Instant lastModified;
        private java.util.Map<String, String> metadata;
        private String etag;
    }
}