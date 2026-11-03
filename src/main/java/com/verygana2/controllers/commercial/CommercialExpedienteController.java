package com.verygana2.controllers.commercial;

import java.util.List;

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

import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentUploadRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;
import com.verygana2.services.interfaces.commercial.CommercialExpedienteDocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Expediente documental del empresario después del onboarding: consultar vigentes e
 * historial, y cargar documentos nuevos o reemplazos (el anterior nunca se borra).
 */
@RestController
@RequestMapping("/commercials/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialExpedienteController {

    private final CommercialExpedienteDocumentService expedienteService;

    @GetMapping
    public ResponseEntity<List<ExpedienteDocumentDTO>> list(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(expedienteService.listForCommercial(userId));
    }

    @PostMapping("/prepare-upload")
    public ResponseEntity<DocumentUploadPermissionDTO> prepareUpload(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpedienteDocumentUploadRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(expedienteService.prepareUpload(userId, dto));
    }

    @PostMapping("/{documentId}/confirm")
    public ResponseEntity<ExpedienteDocumentDTO> confirmUpload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(expedienteService.confirmUpload(userId, documentId));
    }
}
