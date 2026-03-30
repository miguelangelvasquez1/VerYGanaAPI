package com.verygana2.dtos.impactStory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Solicitud del PASO 1 del flujo de media.
 * El admin envía los metadatos del archivo antes de subirlo al CDN.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepareMediaUploadRequestDTO {

    @NotBlank(message = "El nombre del archivo es obligatorio")
    private String originalFileName;

    /** MIME type del archivo (ej. "image/jpeg", "video/mp4") */
    @NotBlank(message = "El content-type es obligatorio")
    private String contentType;

    @NotNull(message = "El tamaño del archivo es obligatorio")
    @Positive(message = "El tamaño debe ser mayor a 0")
    private Long sizeBytes;
}
