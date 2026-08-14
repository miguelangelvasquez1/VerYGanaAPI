package com.verygana2.controllers.compliance;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.compliance.BackgroundCheck;
import com.verygana2.services.interfaces.compliance.BackgroundCheckService;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de antecedentes (ZapSign checks) sobre el representante legal y la empresa de
 * un Contrato Marco. Vive junto a ComplianceContractController porque se dispara desde el
 * mismo panel de contratos, pero en su propio controller para no mezclar responsabilidades.
 */
@RestController
@RequestMapping("/compliance/contracts")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceBackgroundCheckController {

    private final BackgroundCheckService backgroundCheckService;

    /** Dispara una nueva consulta de antecedentes (persona + empresa si aplica). Tiene costo en ZapSign. */
    @PostMapping("/{contractId}/background-checks")
    public ResponseEntity<List<BackgroundCheck>> requestChecks(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long contractId) {
        Long officerId = jwt.getClaim("userId");
        return ResponseEntity.ok(backgroundCheckService.requestChecks(contractId, officerId));
    }

    @GetMapping("/{contractId}/background-checks")
    public ResponseEntity<List<BackgroundCheck>> listByContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(backgroundCheckService.listByContract(contractId));
    }

    /** Reconsulta el estado en ZapSign por si el webhook aún no llegó. */
    @PostMapping("/background-checks/{id}/refresh")
    public ResponseEntity<BackgroundCheck> refresh(@PathVariable Long id) {
        return ResponseEntity.ok(backgroundCheckService.refreshStatus(id));
    }

    /** Hallazgos detallados por fuente, en vivo desde ZapSign. */
    @GetMapping("/background-checks/{id}/detail")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(backgroundCheckService.getDetail(id));
    }
}
