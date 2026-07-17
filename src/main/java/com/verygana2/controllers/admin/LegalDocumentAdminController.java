package com.verygana2.controllers.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.legal.LegalDocumentPrepareUploadRequestDTO;
import com.verygana2.dtos.legal.LegalDocumentResponseDTO;
import com.verygana2.dtos.legal.LegalDocumentUploadPermissionDTO;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.services.interfaces.legal.LegalDocumentService;

import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * Publicación de nuevas versiones de documentos legales (Términos, Privacidad, etc.).
 * El PDF se sube directamente desde el frontend a R2 vía URL pre-firmada — mismo
 * flujo de 3 pasos que los assets de anuncios (ver AdController).
 */
@RestController
@RequestMapping("/admin/legal-documents")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class LegalDocumentAdminController {

    private final LegalDocumentService legalDocumentService;

    /**
     * STEP 1 — Prepare. Crea la fila PENDING y devuelve la URL pre-firmada.
     * Flujo del frontend: 1) este endpoint, 2) PUT del archivo a permission.uploadUrl,
     * 3) POST /{documentId}/confirm.
     */
    @PostMapping("/prepare-upload")
    public ResponseEntity<LegalDocumentUploadPermissionDTO> prepareUpload(
            @Valid @RequestBody LegalDocumentPrepareUploadRequestDTO dto) {
        return ResponseEntity.ok(legalDocumentService.prepareUpload(dto));
    }

    /** STEP 2 — Confirm. Valida lo subido a R2 y publica la versión. */
    @PostMapping("/{documentId}/confirm")
    public ResponseEntity<LegalDocumentResponseDTO> confirmUpload(@PathVariable Long documentId) {
        return ResponseEntity.ok(legalDocumentService.confirmUpload(documentId));
    }

    /** STEP 2.5 — Discard. Cancela una subida pendiente (archivo equivocado, formulario cancelado, etc.). */
    @PostMapping("/discard")
    public ResponseEntity<Void> discardUpload(@RequestBody Map<String, Long> body) {
        Long documentId = body.get("documentId");
        if (documentId == null) {
            throw new ValidationException("documentId es requerido");
        }
        legalDocumentService.discardUpload(documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{type}/history")
    public ResponseEntity<List<LegalDocumentResponseDTO>> history(@PathVariable LegalDocumentType type) {
        return ResponseEntity.ok(legalDocumentService.listHistory(type));
    }
}
