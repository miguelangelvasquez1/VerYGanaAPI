package com.verygana2.storage.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.verygana2.storage.dto.FileMetadata;
import com.verygana2.storage.dto.UploadOptions;
import com.verygana2.storage.dto.UploadResult;
import com.verygana2.storage.dto.UrlOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de CloudStorageService usando Cloudinary
 * Se activa cuando storage.provider=cloudinary
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements CloudStorageService {

    private final Cloudinary cloudinary;

    @Override
    public UploadResult uploadFile(MultipartFile file, String folder, UploadOptions options) {
        log.info("Subiendo archivo a Cloudinary: {} en folder: {}", file.getOriginalFilename(), folder);
        
        try {
            Map<String, Object> uploadParams = buildUploadParams(folder, options);
            
            Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                uploadParams
            );
            
            return mapToUploadResult(result);
            
        } catch (IOException e) {
            log.error("Error al subir archivo a Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Error al subir archivo a Cloudinary", e);
        }
    }

    @Override
    public boolean deleteFile(String publicId) {
        log.info("Eliminando archivo de Cloudinary: {}", publicId);
        
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String resultStatus = (String) result.get("result");
            
            boolean success = "ok".equals(resultStatus);
            if (!success) {
                log.warn("No se pudo eliminar archivo: {} - Status: {}", publicId, resultStatus);
            }
            
            return success;
            
        } catch (IOException e) {
            log.error("Error al eliminar archivo: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String generateUrl(String publicId, UrlOptions options) {
        if (options == null) {
            return cloudinary.url().generate(publicId);
        }
        
        Transformation<?> transformation = new Transformation<>();
        
        if (options.getWidth() != null) {
            transformation.width(options.getWidth());
        }
        if (options.getHeight() != null) {
            transformation.height(options.getHeight());
        }
        if (options.getCrop() != null) {
            transformation.crop(options.getCrop());
        }
        if (options.getQuality() != null) {
            transformation.quality(options.getQuality());
        }
        if (options.getFormat() != null) {
            transformation.fetchFormat(options.getFormat());
        }
        
        return cloudinary.url()
            .transformation(transformation)
            .secure(options.getSecure() != null ? options.getSecure() : true)
            .generate(publicId);
    }

    @Override
    public String generateSignedUrl(String publicId, int expirationMinutes) {
        long expirationTimestamp = System.currentTimeMillis() / 1000 + (expirationMinutes * 60);
        
        Map<String, Object> options = new HashMap<>();
        options.put("sign_url", true);
        options.put("expires_at", expirationTimestamp);
        
        return cloudinary.url()
            .signed(true)
            .generate(publicId);
    }

    @Override
    public List<String> listFiles(String folder) {
        log.debug("Listando archivos en folder: {}", folder);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("type", "upload");
            params.put("prefix", folder);
            params.put("max_results", 500);
            
            Map<?, ?> result = cloudinary.api().resources(params);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
            
            return resources.stream()
                .map(r -> (String) r.get("public_id"))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error al listar archivos: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public FileMetadata getFileMetadata(String publicId) {
        log.debug("Obteniendo metadata de: {}", publicId);
        
        try {
            Map<?, ?> result = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            return mapToFileMetadata(result);
            
        } catch (Exception e) {
            log.error("Error al obtener metadata: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener metadata del archivo", e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            cloudinary.api().ping(ObjectUtils.emptyMap());
            return true;
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ============================================
    // Métodos auxiliares privados
    // ============================================

    private Map<String, Object> buildUploadParams(String folder, UploadOptions options) {
        Map<String, Object> params = new HashMap<>();
        
        // Carpeta
        if (folder != null && !folder.isEmpty()) {
            params.put("folder", folder);
        }
        
        if (options != null) {
            // Resource type
            if (options.getResourceType() != null) {
                params.put("resource_type", options.getResourceType());
            } else {
                params.put("resource_type", "auto");
            }
            
            // Public ID personalizado
            if (options.getPublicId() != null) {
                params.put("public_id", options.getPublicId());
            }
            
            // Tags
            if (options.getTags() != null && !options.getTags().isEmpty()) {
                params.put("tags", new ArrayList<>(options.getTags().values()));
            }
            
            // Metadata
            if (options.getMetadata() != null && !options.getMetadata().isEmpty()) {
                params.put("context", options.getMetadata());
            }
            
            // Sobrescribir
            if (options.getOverwrite() != null) {
                params.put("overwrite", options.getOverwrite());
            }
            
            // Unique filename
            params.put("unique_filename", 
                options.getUniqueFilename() != null ? options.getUniqueFilename() : true);
            
            // Transformación al subir
            if (options.getTransformation() != null) {
                params.put("transformation", buildTransformation(options.getTransformation()));
            }
        }
        
        return params;
    }

    private Transformation<?> buildTransformation(
        com.verygana2.storage.dto.Transformation t) {
        
        Transformation<?> transformation = new Transformation<>();
        
        if (t.getWidth() != null) transformation.width(t.getWidth());
        if (t.getHeight() != null) transformation.height(t.getHeight());
        if (t.getCrop() != null) transformation.crop(t.getCrop());
        if (t.getGravity() != null) transformation.gravity(t.getGravity());
        if (t.getQuality() != null) transformation.quality(t.getQuality());
        if (t.getFormat() != null) transformation.fetchFormat(t.getFormat());
        
        return transformation;
    }

    private UploadResult mapToUploadResult(Map<?, ?> result) {
        return UploadResult.builder()
            .publicId((String) result.get("public_id"))
            .url((String) result.get("url"))
            .secureUrl((String) result.get("secure_url"))
            .format((String) result.get("format"))
            .resourceType((String) result.get("resource_type"))
            .bytes(((Number) result.get("bytes")).longValue())
            .width(result.get("width") != null ? ((Number) result.get("width")).intValue() : null)
            .height(result.get("height") != null ? ((Number) result.get("height")).intValue() : null)
            .duration(result.get("duration") != null ? ((Number) result.get("duration")).doubleValue() : null)
            .createdAt(parseDate(result.get("created_at")))
            .metadata(extractMetadata(result))
            .build();
    }

    private FileMetadata mapToFileMetadata(Map<?, ?> result) {
        return FileMetadata.builder()
            .publicId((String) result.get("public_id"))
            .format((String) result.get("format"))
            .resourceType((String) result.get("resource_type"))
            .bytes(((Number) result.get("bytes")).longValue())
            .width(result.get("width") != null ? ((Number) result.get("width")).intValue() : null)
            .height(result.get("height") != null ? ((Number) result.get("height")).intValue() : null)
            .duration(result.get("duration") != null ? ((Number) result.get("duration")).doubleValue() : null)
            .createdAt(parseDate(result.get("created_at")))
            .updatedAt(parseDate(result.get("updated_at")))
            .url((String) result.get("url"))
            .secureUrl((String) result.get("secure_url"))
            .customMetadata(extractMetadata(result))
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(Map<?, ?> result) {
        Object context = result.get("context");
        if (context instanceof Map) {
            return new HashMap<>((Map<String, Object>) context);
        }
        return new HashMap<>();
    }

    private OffsetDateTime parseDate(Object date) {
        if (date == null) return null;

        try {
            if (date instanceof String) {
                return OffsetDateTime.parse((String) date);
            }
        } catch (Exception e) {
            log.warn("Error al parsear fecha: {}", date);
        }

        return null;
    }
}