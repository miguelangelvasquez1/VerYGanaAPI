package com.verygana2.storage.service;

// import com.verygana2.services.storage.*;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.stereotype.Service;
// import org.springframework.web.multipart.MultipartFile;
// import software.amazon.awssdk.core.sync.RequestBody;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.*;
// import software.amazon.awssdk.services.s3.presigner.S3Presigner;
// import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
// import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

// import java.io.IOException;
// import java.time.Duration;
// import java.time.Instant;
// import java.time.LocalDateTime;
// import java.time.ZoneId;
// import java.util.*;
// import java.util.stream.Collectors;

// /**
//  * Implementación de CloudStorageService usando Cloudflare R2
//  * Se activa cuando storage.provider=r2
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// @ConditionalOnProperty(name = "storage.provider", havingValue = "r2")
// public class R2StorageService implements CloudStorageService {

//     private final S3Client r2Client;
//     private final S3Presigner r2Presigner;
    
//     @Value("${cloudflare.r2.bucket-name}")
//     private String bucketName;
    
//     @Value("${cloudflare.r2.public-domain:}")
//     private String publicDomain;

//     @Override
//     public UploadResult uploadFile(MultipartFile file, String folder, UploadOptions options) {
//         log.info("Subiendo archivo a R2: {} en folder: {}", file.getOriginalFilename(), folder);
        
//         try {
//             String key = generateKey(folder, file.getOriginalFilename(), options);
            
//             Map<String, String> metadata = new HashMap<>();
//             if (options != null && options.getMetadata() != null) {
//                 options.getMetadata().forEach((k, v) -> 
//                     metadata.put(k, v != null ? v.toString() : ""));
//             }
            
//             PutObjectRequest putRequest = PutObjectRequest.builder()
//                 .bucket(bucketName)
//                 .key(key)
//                 .contentType(file.getContentType())
//                 .contentLength(file.getSize())
//                 .metadata(metadata)
//                 .cacheControl("public, max-age=31536000, immutable")
//                 .build();

//             r2Client.putObject(
//                 putRequest,
//                 RequestBody.fromInputStream(file.getInputStream(), file.getSize())
//             );
            
//             log.info("Archivo subido exitosamente a R2: {}", key);
            
//             return buildUploadResult(key, file);
            
//         } catch (IOException e) {
//             log.error("Error al subir archivo a R2: {}", e.getMessage(), e);
//             throw new RuntimeException("Error al subir archivo a R2", e);
//         }
//     }

//     @Override
//     public boolean deleteFile(String publicId) {
//         log.info("Eliminando archivo de R2: {}", publicId);
        
//         try {
//             DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
//                 .bucket(bucketName)
//                 .key(publicId)
//                 .build();

//             r2Client.deleteObject(deleteRequest);
//             return true;
            
//         } catch (S3Exception e) {
//             log.error("Error al eliminar archivo: {}", e.getMessage(), e);
//             return false;
//         }
//     }

//     @Override
//     public String generateUrl(String publicId, UrlOptions options) {
//         // Si hay dominio público configurado
//         if (publicDomain != null && !publicDomain.isEmpty()) {
//             String url = String.format("https://%s/%s", publicDomain, publicId);
            
//             // Agregar transformaciones como query params si es necesario
//             if (options != null) {
//                 url = appendTransformationParams(url, options);
//             }
            
//             return url;
//         }
        
//         // Sin dominio público, generar URL firmada temporal de 1 hora
//         return generateSignedUrl(publicId, 60);
//     }

//     @Override
//     public String generateSignedUrl(String publicId, int expirationMinutes) {
//         GetObjectRequest getRequest = GetObjectRequest.builder()
//             .bucket(bucketName)
//             .key(publicId)
//             .build();

//         GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
//             .signatureDuration(Duration.ofMinutes(expirationMinutes))
//             .getObjectRequest(getRequest)
//             .build();

//         PresignedGetObjectRequest presignedRequest = 
//             r2Presigner.presignGetObject(presignRequest);

//         return presignedRequest.url().toString();
//     }

//     @Override
//     public List<String> listFiles(String folder) {
//         log.debug("Listando archivos en folder: {}", folder);
        
//         try {
//             ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
//                 .bucket(bucketName)
//                 .prefix(folder)
//                 .maxKeys(1000)
//                 .build();

//             ListObjectsV2Response response = r2Client.listObjectsV2(listRequest);
            
//             return response.contents().stream()
//                 .map(S3Object::key)
//                 .collect(Collectors.toList());
                
//         } catch (S3Exception e) {
//             log.error("Error al listar archivos: {}", e.getMessage(), e);
//             return Collections.emptyList();
//         }
//     }

//     @Override
//     public FileMetadata getFileMetadata(String publicId) {
//         log.debug("Obteniendo metadata de: {}", publicId);
        
//         try {
//             HeadObjectRequest headRequest = HeadObjectRequest.builder()
//                 .bucket(bucketName)
//                 .key(publicId)
//                 .build();

//             HeadObjectResponse response = r2Client.headObject(headRequest);
            
//             return FileMetadata.builder()
//                 .publicId(publicId)
//                 .format(extractFormat(publicId))
//                 .resourceType(determineResourceType(response.contentType()))
//                 .bytes(response.contentLength())
//                 .createdAt(convertToLocalDateTime(response.lastModified()))
//                 .url(generateUrl(publicId, null))
//                 .secureUrl(generateUrl(publicId, null))
//                 .customMetadata(new HashMap<>(response.metadata()))
//                 .build();
                
//         } catch (S3Exception e) {
//             log.error("Error al obtener metadata: {}", e.getMessage(), e);
//             throw new RuntimeException("Error al obtener metadata del archivo", e);
//         }
//     }

//     @Override
//     public boolean isHealthy() {
//         try {
//             ListBucketsRequest request = ListBucketsRequest.builder().build();
//             r2Client.listBuckets(request);
//             return true;
//         } catch (Exception e) {
//             log.error("Health check failed: {}", e.getMessage());
//             return false;
//         }
//     }

//     // ============================================
//     // Métodos auxiliares privados
//     // ============================================

//     private String generateKey(String folder, String originalFilename, UploadOptions options) {
//         String uuid = UUID.randomUUID().toString();
//         String extension = extractFormat(originalFilename);
        
//         // Si hay publicId personalizado en options
//         if (options != null && options.getPublicId() != null) {
//             return String.format("%s/%s.%s", folder, options.getPublicId(), extension);
//         }
        
//         // Si no, usar UUID
//         return String.format("%s/%s.%s", folder, uuid, extension);
//     }

//     private UploadResult buildUploadResult(String key, MultipartFile file) {
//         return UploadResult.builder()
//             .publicId(key)
//             .url(generateUrl(key, null))
//             .secureUrl(generateUrl(key, null))
//             .format(extractFormat(file.getOriginalFilename()))
//             .resourceType(determineResourceType(file.getContentType()))
//             .bytes(file.getSize())
//             .createdAt(LocalDateTime.now())
//             .metadata(new HashMap<>())
//             .build();
//     }

//     private String extractFormat(String filename) {
//         if (filename == null || !filename.contains(".")) {
//             return "";
//         }
//         return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
//     }

//     private String determineResourceType(String contentType) {
//         if (contentType == null) return "raw";
        
//         if (contentType.startsWith("image/")) return "image";
//         if (contentType.startsWith("video/")) return "video";
//         if (contentType.startsWith("audio/")) return "audio";
        
//         return "raw";
//     }

//     private LocalDateTime convertToLocalDateTime(Instant instant) {
//         if (instant == null) return null;
//         return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
//     }

//     private String appendTransformationParams(String url, UrlOptions options) {
//         // Para R2, las transformaciones se harían con Cloudflare Images
//         // o un servicio separado. Por ahora, retornamos la URL base.
//         // En producción, podrías integrar con Cloudflare Images Transform API
//         return url;
//     }
// }