package com.verygana2.dtos.user.commercial.onboarding;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * El PDF de Términos y Condiciones y su URL de descarga viven en el .env del
 * frontend; el backend no los sirve, solo registra qué versión/documento fue
 * mostrado y aceptado por el usuario.
 */
@Data
public class TermsAcceptanceRequestDTO {

    @NotBlank(message = "La versión de los términos y condiciones es requerida")
    private String termsVersion;

    @NotBlank(message = "La URL del documento de términos y condiciones es requerida")
    private String termsDocumentUrl;

    private LocalDate termsPublishedDate;

    @AssertTrue(message = "Debe aceptar los Términos y Condiciones para continuar")
    private boolean accepted;
}
