package com.verygana2.services.interfaces.commercial;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;

public interface CommercialOnboardingService {

    CommercialOnboardingStatusResponseDTO getStatus(Long userId);

    CommercialOnboardingStatusResponseDTO acceptTerms(Long userId, TermsAcceptanceRequestDTO dto, String ipAddress, String userAgent);

    CommercialOnboardingStatusResponseDTO submitLegalIdentification(Long userId, LegalIdentificationRequestDTO dto);

    RouteClassificationResponseDTO submitDiagnostic(Long userId, CommercialDiagnosticRequestDTO dto);

    RouteClassificationResponseDTO getClassification(Long userId);

    CommercialOnboardingStatusResponseDTO confirmClassification(Long userId);

    /** Paso 6-7: plan recomendado según la ruta + resumen económico completo, antes de aceptar. */
    PlanSummaryResponseDTO getRecommendedPlan(Long userId);

    /** El comercial acepta el plan y sus condiciones económicas (tarifa, comisión, impuestos, liquidación). */
    PlanSummaryResponseDTO acceptPlan(Long userId);
}
