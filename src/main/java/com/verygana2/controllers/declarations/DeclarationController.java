package com.verygana2.controllers.declarations;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.declarations.DeclarationDTOs.DeclarationView;
import com.verygana2.dtos.declarations.DeclarationDTOs.Manifestation;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.DisclosureContext;
import com.verygana2.services.interfaces.declarations.DeclarationService;

import lombok.RequiredArgsConstructor;

/**
 * Consulta de declaraciones contractuales. Aquí no se acepta nada: la manifestación se
 * recoge solo en la Hoja Final del contrato (POST /commercials/onboarding/contract/approve).
 */
@RestController
@RequiredArgsConstructor
public class DeclarationController {

    private final DeclarationService declarationService;

    /** Texto literal vigente y evidencia existente. Registra que se puso a disposición. */
    @GetMapping("/commercials/declarations/{code}")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<DeclarationView> view(@AuthenticationPrincipal Jwt jwt, @PathVariable DeclarationCode code,
            @RequestParam(defaultValue = "TAX_PROFILE") DisclosureContext context) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(declarationService.viewForCommercial(userId, code, context));
    }

    /** Historial completo: manifestaciones, ausencias, negativas e incidencias técnicas. */
    @GetMapping("/compliance/commercials/{commercialPublicId}/declarations/{code}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<List<Manifestation>> history(@PathVariable UUID commercialPublicId,
            @PathVariable DeclarationCode code) {
        return ResponseEntity.ok(declarationService.historyForCompliance(commercialPublicId, code));
    }
}
