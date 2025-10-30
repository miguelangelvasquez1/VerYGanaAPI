package com.verygana2.storage.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Opciones para generar URLs
 */
@Data
@Builder
public class UrlOptions {
    private Integer width;
    private Integer height;
    private String crop;           // "fill", "fit", "scale", etc.
    private String format;         // "jpg", "png", "webp", "auto"
    private Integer qualityInt;       // 1-100
    private String effect;         // Efectos especiales
    private Boolean secure;        // Usar HTTPS
    
    @Builder.Default
    private String fetchFormat = "auto";
    
    @Builder.Default
    private String quality = "auto";
}
