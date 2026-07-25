package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.OnboardingStep;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila del listado de onboardings pendientes de negociación con un asesor
 * (Ruta D: integración técnica; Ruta E: negociación especial), panel de
 * compliance. Puede no existir todavía un CommercialContract (se bloquea
 * antes de generarlo), así que se lista por onboardingId, no por contractId.
 *
 * companyName/email/phoneNumber/pep quedan planos para la fila de la tabla;
 * legalIdentification trae el resto de datos jurídicos completos para el
 * panel de detalle. plan viene null en Ruta D (nunca pasa por selección de
 * plan) y con los valores ya congelados (snapshot) en Ruta E.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorNegotiationListItemDTO {
    private Long onboardingId;
    private Long userId;

    private String companyName;
    private String email;
    private String phoneNumber;
    private boolean pep;

    private LegalIdentificationSummaryDTO legalIdentification;
    private PlanSummaryResponseDTO plan;

    private CommercialRoute route;
    private String routeExplanation;

    /** Qué necesita, según la ruta: integración técnica (D) o negociación especial (E). */
    private String integrationDetails;
    private String specialNegotiationDetails;

    private OnboardingStep currentStep;
    private ZonedDateTime classifiedAt;
}
