package com.verygana2.dtos.user.commercial.expediente;

import java.time.LocalDate;

import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Revisión de contenido de un documento del expediente por compliance. */
@Data
public class DocumentReviewRequestDTO {

    @NotNull(message = "El resultado de la revisión es requerido")
    private CommercialDocumentReviewStatus result;

    /** NIT o cédula del titular tal como figura en el documento. */
    @Size(max = 30)
    private String holderIdentification;

    private LocalDate issuedAt;

    /** Solo si el documento tiene una vigencia aplicable y comprobable; si no, null. */
    private LocalDate expiresAt;

    @Size(max = 1000)
    private String notes;
}
