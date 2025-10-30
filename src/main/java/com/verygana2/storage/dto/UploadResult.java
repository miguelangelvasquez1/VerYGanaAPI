package com.verygana2.storage.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Resultado de una operación de upload
 */
@Data
@Builder
public class UploadResult {
    private String publicId;      // ID único del archivo
    private String url;            // URL pública
    private String secureUrl;      // URL HTTPS
    private String format;         // Formato del archivo (jpg, mp4, etc.)
    private String resourceType;   // Tipo de recurso (image, video, raw)
    private Long bytes;            // Tamaño en bytes
    private Integer width;         // Ancho (para imágenes/videos)
    private Integer height;        // Alto (para imágenes/videos)
    private Double duration;       // Duración en segundos (para videos)
    private OffsetDateTime createdAt;
    private Map<String, Object> metadata; // Metadata adicional
}
