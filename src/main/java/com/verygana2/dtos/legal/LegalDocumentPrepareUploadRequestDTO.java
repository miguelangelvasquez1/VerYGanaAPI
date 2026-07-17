package com.verygana2.dtos.legal;

import com.verygana2.models.enums.legal.LegalDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * version y publishedDate NO se reciben del cliente: version se calcula
 * incrementando la última versión existente para ese tipo (ver
 * LegalDocumentServiceImpl.computeNextVersion), y publishedDate es la fecha
 * del servidor al momento de preparar la subida (LocalDate.now()).
 */
@Data
public class LegalDocumentPrepareUploadRequestDTO {

    @NotNull(message = "El tipo de documento es requerido")
    private LegalDocumentType type;

    @NotBlank(message = "El nombre del archivo es requerido")
    private String originalFileName;

    @NotBlank(message = "El content-type es requerido")
    private String contentType;

    @NotNull(message = "El tamaño del archivo es requerido")
    @Positive(message = "El tamaño del archivo debe ser positivo")
    private Long sizeBytes;
}
