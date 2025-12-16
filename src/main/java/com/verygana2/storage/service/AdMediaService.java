package com.verygana2.storage.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.MediaType;
import com.verygana2.repositories.AdRepository;
import com.verygana2.storage.dto.UploadOptions;
import com.verygana2.storage.dto.UploadResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdMediaService {

    private final AdRepository adRepository;
    private final CloudStorageService storageService;
    private final AdMapper adMapper;

    /**
     * Sube contenido multimedia para un anuncio
     * Funciona igual sin importar el provider (Cloudinary o R2)
     */
    @Transactional
    public AdResponseDTO uploadAdMedia(Long adId, MultipartFile file, MediaType mediaType) {
        
        log.info("Subiendo media para anuncio {}: tipo {}", adId, mediaType);
        
        // Buscar anuncio
        Objects.requireNonNull(adId, "adId no puede ser nulo");
        Ad ad = adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException(adId));
        
        String mediaTypeStr = mediaType == MediaType.VIDEO ? "video" : "image";
        // Validar tipo de archivo
        validateMediaType(file, mediaTypeStr);
        
        // Preparar opciones de upload
        String folder = String.format("ads/%d", adId);
        
        UploadOptions options = UploadOptions.builder()
            .resourceType(mediaTypeStr) // "image" o "video"
            .folder(folder)
            .uniqueFilename(true)
            .metadata(buildMetadata(ad))
            .build();
        
        // Subir archivo (funciona igual con Cloudinary o R2)
        UploadResult result = storageService.uploadFile(file, folder, options);

        //Si hay un media previo, eliminarlo de la nube
        if (ad.getContentUrl() != null) {
            String previousPublicId = extractPublicIdFromUrl(ad.getContentUrl());
            String resourceType = getResourceType(ad.getContentUrl());
            boolean deleted = storageService.deleteFile(previousPublicId, resourceType);
            if (deleted) log.info("Media previo eliminado: {}", previousPublicId);
        }
        
        // Actualizar entidad Ad
        ad.setContentUrl(result.getSecureUrl());
        ad = adRepository.save(ad);
        
        log.info("Media subido exitosamente: {}, fecha: {}", result.getPublicId(), result.getCreatedAt());
        
        return adMapper.toDto(ad);
    }

    /**
     * Elimina contenido multimedia de un anuncio
     */
    @Transactional
    public void deleteAdMedia(Long adId) {
        log.info("Eliminando media de anuncio {}", adId);
        
        Objects.requireNonNull(adId, "adId no puede ser nulo");
        Ad ad = adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException(adId));
        
        if (ad.getContentUrl() != null) {
            // Extraer publicId de la URL
            String publicId = extractPublicIdFromUrl(ad.getContentUrl());
            String resourceType = getResourceType(ad.getContentUrl());
            
            // Eliminar del storage
            boolean deleted = storageService.deleteFile(publicId, resourceType);
            
            if (deleted) {
                ad.setContentUrl(null);
                adRepository.save(ad);
                log.info("Media eliminado exitosamente");
            } else {
                log.warn("No se pudo eliminar el media del storage");
                throw new RuntimeException("No se pudo eliminar el media del storage");
            }
        }
    }

    /**
     * Genera URL optimizada para mostrar el contenido
     */
    public String generateOptimizedUrl(String publicId, int width, int height) {
        return storageService.generateUrl(
            publicId,
            com.verygana2.storage.dto.UrlOptions.builder()
                .width(width)
                .height(height)
                .crop("fill")
                .qualityInt(80)
                .format("auto")
                .secure(true)
                .build()
        );
    }

    // ============================================
    // Métodos auxiliares privados
    // ============================================

    private void validateMediaType(MultipartFile file, String mediaType) {
        String contentType = file.getContentType();

        if (contentType == null) throw new NullPointerException("contenType is null or file is null");
        
        if ("video".equals(mediaType)) {
            if (!contentType.startsWith("video/")) {
                throw new IllegalArgumentException("El archivo debe ser un video");
            }
            // Validar tamaño máximo para videos (100MB)
            if (file.getSize() > 100 * 1024 * 1024) {
                throw new IllegalArgumentException("Video muy grande (máx 100MB)");
            }
        } else if ("image".equals(mediaType)) {
            if (!contentType.startsWith("image/")) {
                throw new IllegalArgumentException("El archivo debe ser una imagen");
            }
            // Validar tamaño máximo para imágenes (5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("Imagen muy grande (máx 5MB)");
            }
        } else {
            throw new IllegalArgumentException("Tipo de media no válido: " + mediaType);
        }
    }

    private Map<String, Object> buildMetadata(Ad ad) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ad_id", ad.getId());
        metadata.put("advertiser_id", ad.getAdvertiser().getId());
        metadata.put("uploaded_at", java.time.Instant.now().toString());
        return metadata;
    }

    private String extractPublicIdFromUrl(String url) {
        // Para Cloudinary: extraer public_id de la URL
        // Para R2: extraer key de la URL
        // Esta lógica depende del formato de URL de cada provider
        
        // Cloudinary: https://res.cloudinary.com/cloud-name/image/upload/v123/ads/1/uuid.jpg
        if (url.contains("cloudinary.com")) {
            String[] parts = url.split("/upload/");
            if (parts.length > 1) {
                String path = parts[1];
                // Remover versión si existe
                if (path.startsWith("v")) {
                    path = path.substring(path.indexOf("/") + 1);
                }
                // Remover extensión
                return path.substring(0, path.lastIndexOf("."));
            }
        }
        
        // R2: https://cdn.tudominio.com/ads/1/uuid.jpg
        if (url.contains(storageService.getClass().getSimpleName().contains("R2") ? "/" : "cloudinary")) {
            // Para R2, el path completo es el publicId
            String path = url.substring(url.indexOf(".com/") + 5);
            return path;
        }
        
        return url;
    }

    public static String getResourceType(String cloudinaryUrl) {
        if (cloudinaryUrl == null) return null;
        // Divide la URL en partes
        String[] parts = cloudinaryUrl.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("upload") && i > 0) {
                return parts[i - 1]; // palabra anterior a "upload"
            }
        }
        return null;
    }
}