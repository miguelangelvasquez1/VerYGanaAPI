package com.verygana2.storage.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Opciones para upload
 */
@Data
@Builder
public class UploadOptions {
    private String resourceType;   // "image", "video", "raw"
    private String folder;          // Carpeta destino
    private String publicId;        // ID personalizado (opcional)
    private Map<String, String> tags; // Tags para organización
    private Map<String, Object> metadata; // Metadata custom
    private Boolean overwrite;      // Sobrescribir si existe
    private Transformation transformation; // Transformaciones al subir
    
    @Builder.Default
    private Boolean useFilename = false;
    
    @Builder.Default
    private Boolean uniqueFilename = true;
}