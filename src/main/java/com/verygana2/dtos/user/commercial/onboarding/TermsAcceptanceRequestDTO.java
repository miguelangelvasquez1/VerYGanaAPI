package com.verygana2.dtos.user.commercial.onboarding;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * El PDF de Términos y Condiciones y su URL viven en la tabla legal_documents
 * (ver LegalDocumentController) — el frontend solo confirma qué versión mostró
 * y si el usuario la aceptó; el backend resuelve la URL/fecha reales desde la
 * BD (no confía en lo que el cliente declare).
 */
@Data
public class TermsAcceptanceRequestDTO {

    @NotBlank(message = "La versión de los términos y condiciones es requerida")
    private String termsVersion;

    @AssertTrue(message = "Debe aceptar los Términos y Condiciones para continuar")
    private boolean accepted;
}
