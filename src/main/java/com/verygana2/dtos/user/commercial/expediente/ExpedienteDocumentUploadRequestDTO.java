package com.verygana2.dtos.user.commercial.expediente;

import com.verygana2.models.enums.commercial.CommercialDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Carga de un documento al expediente después del onboarding (nuevo o reemplazo de uno vigente). */
@Data
public class ExpedienteDocumentUploadRequestDTO {

    @NotNull(message = "El tipo de documento es requerido")
    private CommercialDocumentType documentType;

    @NotBlank(message = "El nombre del archivo es requerido")
    private String originalFileName;

    @NotBlank(message = "El content-type es requerido")
    private String contentType;

    @NotNull(message = "El tamaño del archivo es requerido")
    @Positive(message = "El tamaño del archivo debe ser positivo")
    private Long sizeBytes;

    /**
     * Documento vigente que esta carga reemplaza. Al confirmar, el anterior queda en el
     * historial como SUPERSEDED; no se borra.
     */
    private Long replacesDocumentId;
}
