package com.verygana2.storage.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.exceptions.StorageException;
import com.verygana2.storage.config.R2Config;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Slf4j
public class R2Service {

    @Autowired
    private S3Client r2Client;

    @Autowired
    private S3Presigner r2Presigner;

    @Autowired
    private R2Config r2Config;

    /**
     * Genera URL pre-firmada para subir un objeto
     */
    public AssetUploadPermissionDTO generateUploadUrl(
            String objectKey,
            String contentType,
            Long expirationSeconds) {
        
        try {
            // Validar parámetros
            validateObjectKey(objectKey);
            
            // Crear request de pre-signed URL
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .metadata(java.util.Map.of(
                    "uploaded-at", Instant.now().toString(),
                    "status", "pending"
                ))
                .build();

            // Configurar expiración
            Duration expiration = Duration.ofSeconds(
                expirationSeconds != null ? expirationSeconds : 3600L
            );

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putRequest)
                .build();

            // Generar URL firmada
            PresignedPutObjectRequest presignedRequest = 
                r2Presigner.presignPutObject(presignRequest);

            String uploadUrl = presignedRequest.url().toString();
            String publicUrl = buildPublicUrl(objectKey);

            log.info("Pre-signed URL generada para: {}", objectKey);

            return new AssetUploadPermissionDTO(
                uploadUrl,
                publicUrl,
                expiration.getSeconds()
            );

        } catch (Exception e) {
            log.error("Error generando pre-signed URL para {}: {}", objectKey, e.getMessage());
            throw new StorageException("Error generando URL de subida", e);
        }
    }

    /**
     * Verifica si un objeto existe en R2
     */
    public boolean objectExists(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(objectKey)
                .build();

            r2Client.headObject(headRequest);
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
        // Si tienes CDN configurado (ej: images.verygana.com)
        if (r2Config.getCdnDomain() != null && !r2Config.getCdnDomain().isEmpty()) {
            return String.format("https://%s/%s", r2Config.getCdnDomain(), objectKey);
        }
        
        // Fallback a URL pública de R2
        return String.format("https://%s.r2.cloudflarestorage.com/%s",
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
    // public String copyObject(String sourceKey, String destinationKey) {
    //     try {
    //         CopyObjectRequest copyRequest = CopyObjectRequest.builder()
    //             .sourceBucket(r2Config.getBucketName())
    //             .sourceKey(sourceKey)
    //             .destinationBucket(r2Config.getBucketName())
    //             .destinationKey(destinationKey)
    //             .build();

    //         CopyObjectResponse response = r2Client.copyObject(copyRequest);
    //         log.info("Objeto copiado: {} -> {}", sourceKey, destinationKey);
            
    //         return buildPublicUrl(destinationKey);

    //     } catch (Exception e) {
    //         log.error("Error copiando objeto: {}", e.getMessage());
    //         throw new StorageException("Error copiando objeto", e);
    //     }
    // }

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