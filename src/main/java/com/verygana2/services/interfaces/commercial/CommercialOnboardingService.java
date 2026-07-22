package com.verygana2.services.interfaces.commercial;

import com.verygana2.dtos.user.commercial.onboarding.AcceptPlanRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanComparisonResponseDTO;
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

    /**
     * Paso 6-7: catálogo completo de planes activos, marcando cuál es el recomendado
     * según la ruta, con todos los datos necesarios para una tabla comparativa en el front.
     */
    PlanComparisonResponseDTO getRecommendedPlan(Long userId);

    /**
     * El comercial acepta un plan (no necesariamente el recomendado) y sus condiciones
     * económicas (tarifa, comisión, impuestos, liquidación) quedan congeladas para el contrato.
     */
    PlanSummaryResponseDTO acceptPlan(Long userId, AcceptPlanRequestDTO dto);

    /**
     * Resumen de solo lectura de todo lo capturado en los pasos 2-8 (T&C, identificación
     * jurídica, diagnóstico, clasificación, plan y documentos). Pensado para mostrarse
     * ANTES de generar el Contrato Marco, así el comercial revisa sus datos sin tener
     * que generar un PDF nuevo solo para verificarlos.
     */
    CommercialOnboardingSummaryResponseDTO getSummary(Long userId);
}
