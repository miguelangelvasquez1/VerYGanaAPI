package com.verygana2.controllers.inforequests;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertReviewRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CancelRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CreateRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ReviewRequest;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.services.interfaces.inforequests.ComplianceAlertService;
import com.verygana2.services.interfaces.inforequests.ComplianceCaseService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Requerimientos, hallazgos, incidencias y alertas del expediente (4.7 / 4.8). Solo un
 * hallazgo con fundamento contractual puede cambiar el estado de la cuenta.
 */
@RestController
@RequestMapping("/compliance")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceInformationRequestController {

    private final InformationRequestService requestService;
    private final ComplianceCaseService caseService;
    private final ComplianceAlertService alertService;

    // ---- Requerimientos ----

    @GetMapping("/information-requests")
    public ResponseEntity<PagedResponse<RequestView>> search(
            @RequestParam(required = false) InformationRequestStatus status,
            @RequestParam(required = false) UUID commercialPublicId,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(requestService.search(status, commercialPublicId, pageable));
    }

    @GetMapping("/information-requests/{requestPublicId}")
    public ResponseEntity<RequestView> get(@PathVariable UUID requestPublicId) {
        return ResponseEntity.ok(requestService.getForCompliance(requestPublicId));
    }

    @PostMapping("/information-requests")
    public ResponseEntity<RequestView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.create(request, userId));
    }

    @PostMapping("/information-requests/{requestPublicId}/review")
    public ResponseEntity<RequestView> review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestPublicId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.review(requestPublicId, userId, request));
    }

    @PostMapping("/information-requests/{requestPublicId}/cancel")
    public ResponseEntity<RequestView> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestPublicId,
            @Valid @RequestBody CancelRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.cancel(requestPublicId, userId, request));
    }

    // ---- Hallazgos e incidencias ----

    /** Única vía para aplicar una medida (restricción preventiva, suspensión) sobre la cuenta. */
    @PostMapping("/findings")
    public ResponseEntity<FindingView> recordFinding(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FindingRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(caseService.recordFinding(request, userId));
    }

    @GetMapping("/commercials/{commercialPublicId}/findings")
    public ResponseEntity<List<FindingView>> findings(@PathVariable UUID commercialPublicId) {
        return ResponseEntity.ok(caseService.listFindings(commercialPublicId));
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentView> recordIncident(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IncidentRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(caseService.recordIncident(request, userId));
    }

    @GetMapping("/commercials/{commercialPublicId}/incidents")
    public ResponseEntity<List<IncidentView>> incidents(@PathVariable UUID commercialPublicId) {
        return ResponseEntity.ok(caseService.listIncidents(commercialPublicId));
    }

    // ---- Alertas y reglas ----

    @GetMapping("/alerts")
    public ResponseEntity<PagedResponse<AlertView>> alerts(@RequestParam(defaultValue = "true") boolean pendingOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(alertService.listAlerts(pendingOnly, pageable));
    }

    @PostMapping("/alerts/{alertId}/review")
    public ResponseEntity<AlertView> reviewAlert(@AuthenticationPrincipal Jwt jwt, @PathVariable Long alertId,
            @Valid @RequestBody AlertReviewRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(alertService.reviewAlert(alertId, userId, request));
    }

    @GetMapping("/alert-rules")
    public ResponseEntity<List<AlertRuleView>> rules() {
        return ResponseEntity.ok(alertService.listRules());
    }

    /** Crea la regla o una versión nueva del mismo código. Sus umbrales los define compliance. */
    @PostMapping("/alert-rules")
    public ResponseEntity<AlertRuleView> createRule(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AlertRuleRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(alertService.createRule(request, userId));
    }

    @PostMapping("/alert-rules/{ruleId}/deactivate")
    public ResponseEntity<AlertRuleView> deactivateRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(alertService.deactivateRule(ruleId));
    }
}
