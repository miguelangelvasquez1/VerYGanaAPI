package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.commercial.CommercialDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DocumentUploadRequestDTO {

    @NotNull(message = "El tipo de documento es requerido")
    private CommercialDocumentType documentType;

    @NotBlank(message = "El nombre del archivo es requerido")
    private String originalFileName;

    @NotBlank(message = "El content-type es requerido")
    private String contentType;

    @NotNull(message = "El tamaño del archivo es requerido")
    @Positive(message = "El tamaño del archivo debe ser positivo")
    private Long sizeBytes;
}
