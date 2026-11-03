package com.verygana2.controllers.compliance;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.expediente.DocumentReviewRequestDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentDTO;
import com.verygana2.services.interfaces.commercial.CommercialExpedienteDocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Revisión de contenido de los documentos del expediente del empresario (4.7 / 4.8). */
@RestController
@RequestMapping("/compliance")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceExpedienteController {

    private final CommercialExpedienteDocumentService expedienteService;

    /** Documentos vigentes e historial (reemplazados) del empresario. */
    @GetMapping("/commercials/{commercialPublicId}/documents")
    public ResponseEntity<List<ExpedienteDocumentDTO>> listDocuments(@PathVariable UUID commercialPublicId) {
        return ResponseEntity.ok(expedienteService.listForCompliance(commercialPublicId));
    }

    @PostMapping("/commercial-documents/{documentId}/review")
    public ResponseEntity<ExpedienteDocumentDTO> review(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId,
            @Valid @RequestBody DocumentReviewRequestDTO dto) {
        Long reviewerUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(expedienteService.review(documentId, reviewerUserId, dto));
    }
}
