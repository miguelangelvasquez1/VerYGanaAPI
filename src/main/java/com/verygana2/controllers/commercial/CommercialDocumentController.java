package com.verygana2.controllers.commercial;

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

import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentsStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadRequestDTO;
import com.verygana2.services.interfaces.commercial.CommercialDocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Paso 8 - Carga documental (RUT, Cámara de Comercio, cédula, certificación bancaria, etc.). */
@RestController
@RequestMapping("/commercials/onboarding/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class CommercialDocumentController {

    private final CommercialDocumentService documentService;

    @GetMapping
    public ResponseEntity<CommercialDocumentsStatusResponseDTO> getStatus(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(documentService.getStatus(userId));
    }

    @PostMapping("/prepare-upload")
    public ResponseEntity<DocumentUploadPermissionDTO> prepareUpload(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentUploadRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(documentService.prepareUpload(userId, dto));
    }

    @PostMapping("/{documentId}/confirm")
    public ResponseEntity<CommercialDocumentsStatusResponseDTO> confirmUpload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(documentService.confirmUpload(userId, documentId));
    }

    @PostMapping("/{documentId}/discard")
    public ResponseEntity<CommercialDocumentsStatusResponseDTO> discard(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long documentId) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(documentService.discard(userId, documentId));
    }

    /** Botón "Continuar" del tab de Documentos: confirma la carga completa y avanza a CONTRACT_PENDING. */
    @PostMapping("/continue")
    public ResponseEntity<CommercialDocumentsStatusResponseDTO> continueToContract(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(documentService.continueToContract(userId));
    }
}
