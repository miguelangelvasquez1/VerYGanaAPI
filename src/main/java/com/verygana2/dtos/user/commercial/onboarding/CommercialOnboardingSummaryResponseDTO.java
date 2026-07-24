package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen de todo lo capturado a lo largo del onboarding comercial (pasos 2-8),
 * de solo lectura — pensado para mostrarse ANTES de generar el Contrato Marco
 * (POST /commercials/onboarding/contract/generate), así el comercial revisa
 * los datos sin necesitar un PDF nuevo cada vez que quiere corregir algo.
 *
 * Cada bloque viene null si el paso correspondiente aún no se completó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommercialOnboardingSummaryResponseDTO {
    private String termsVersion;
    private ZonedDateTime termsAcceptedAt;

    private LegalIdentificationSummaryDTO legalIdentification;
    private DiagnosticSummaryDTO diagnostic;
    private RouteClassificationResponseDTO classification;
    private PlanSummaryResponseDTO plan;
    private CommercialDocumentsStatusResponseDTO documents;
}
