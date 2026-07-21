package com.verygana2.controllers.commercial;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.onboarding.AcceptPlanRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanComparisonResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;
import com.verygana2.security.auth.RequestClientInfo;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Flujo de registro comercial extendido, posterior al registro básico
 * (POST /auth/register/commercial): aceptación de Términos y Condiciones,
 * identificación jurídica, diagnóstico comercial y clasificación automática
 * de ruta (A-E).
 */
@RestController
@RequestMapping("/commercials/onboarding")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class CommercialOnboardingController {

    private final CommercialOnboardingService onboardingService;

    @GetMapping("/status")
    public ResponseEntity<CommercialOnboardingStatusResponseDTO> getStatus(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    /**
     * Resumen de solo lectura de todo lo capturado en los pasos 2-8, para mostrarse
     * ANTES de generar el Contrato Marco — así el comercial revisa sus datos sin
     * necesitar un PDF nuevo cada vez que quiere corregir algo antes de generarlo.
     */
    @GetMapping("/summary")
    public ResponseEntity<CommercialOnboardingSummaryResponseDTO> getSummary(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.getSummary(userId));
    }

    /** Paso 2: lectura y aceptación de Términos y Condiciones. */
    @PostMapping("/terms")
    public ResponseEntity<CommercialOnboardingStatusResponseDTO> acceptTerms( 
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TermsAcceptanceRequestDTO dto,
            HttpServletRequest request) {
        Long userId = jwt.getClaim("userId");
        String ip = RequestClientInfo.resolveIp(request);
        String userAgent = RequestClientInfo.resolveUserAgent(request);
        return ResponseEntity.ok(onboardingService.acceptTerms(userId, dto, ip, userAgent));
    }

    /** Paso 3: identificación jurídica. */
    @PostMapping("/legal-identification")
    public ResponseEntity<CommercialOnboardingStatusResponseDTO> submitLegalIdentification(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LegalIdentificationRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.submitLegalIdentification(userId, dto));
    }

    /** Paso 4: diagnóstico comercial. Devuelve directamente la clasificación calculada (paso 5). */
    @PostMapping("/diagnostic")
    public ResponseEntity<RouteClassificationResponseDTO> submitDiagnostic(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommercialDiagnosticRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.submitDiagnostic(userId, dto));
    }

    /** Paso 5: consultar la clasificación (ruta + explicación) antes de confirmar. */
    @GetMapping("/classification")
    public ResponseEntity<RouteClassificationResponseDTO> getClassification(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.getClassification(userId));
    }

    /** Paso 5: el empresario confirma haber visto la explicación de su ruta y avanza al paso 6. */
    @PostMapping("/classification/confirm")
    public ResponseEntity<CommercialOnboardingStatusResponseDTO> confirmClassification(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.confirmClassification(userId));
    }

    /** Paso 6-7: catálogo completo de planes (para tabla comparativa), marcando el recomendado según la ruta. */
    @GetMapping("/plan")
    public ResponseEntity<PlanComparisonResponseDTO> getRecommendedPlan(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.getRecommendedPlan(userId));
    }

    /** Paso 7: el empresario acepta un plan (no necesariamente el recomendado) y sus condiciones económicas. */
    @PostMapping("/plan/accept")
    public ResponseEntity<PlanSummaryResponseDTO> acceptPlan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AcceptPlanRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(onboardingService.acceptPlan(userId, dto));
    }
}
