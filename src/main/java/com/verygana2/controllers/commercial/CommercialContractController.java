package com.verygana2.controllers.commercial;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.services.interfaces.commercial.CommercialContractService;

import lombok.RequiredArgsConstructor;

/**
 * Pasos 9-10: generación del Contrato Marco y revisión del empresario
 * (vista previa, descarga, aprobación o solicitud de cambios).
 */
@RestController
@RequestMapping("/commercials/onboarding/contract")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class CommercialContractController {

    private final CommercialContractService contractService;

    /** Paso 9: genera (o regenera) el Contrato Marco + anexo + resumen económico + documentos aceptados. */
    @PostMapping("/generate")
    public ResponseEntity<ContractSummaryResponseDTO> generate(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.generate(userId));
    }

    /** Paso 10: vista previa / descarga del contrato vigente. */
    @GetMapping
    public ResponseEntity<ContractSummaryResponseDTO> getCurrent(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.getCurrent(userId));
    }

    /** Paso 10: el empresario aprueba el contrato revisado y lo envía a revisión de VERYGANA (paso 11). */
    @PostMapping("/approve")
    public ResponseEntity<ContractSummaryResponseDTO> approve(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.businessApprove(userId));
    }

    /**
     * Paso 10: el empresario regresa a corregir campos no jurídicos (diagnóstico, plan o documentos).
     * Devuelve el paso al que quedó el onboarding para que el front redirija ahí directamente.
     */
    @PostMapping("/request-changes")
    public ResponseEntity<OnboardingStep> requestChanges(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.requestChanges(userId));
    }
}
