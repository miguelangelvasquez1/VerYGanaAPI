package com.verygana2.controllers.legal;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.legal.LegalDocumentResponseDTO;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.services.interfaces.legal.LegalDocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Consulta pública de documentos legales vigentes (Términos y Condiciones,
 * Política de Privacidad, etc.). No requiere autenticación: se muestran en
 * pantallas públicas (registro, footer) además de dentro del onboarding.
 */
@RestController
@RequestMapping("/legal-documents")
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentService legalDocumentService;

    @GetMapping
    public ResponseEntity<List<LegalDocumentResponseDTO>> listAllActive() {
        return ResponseEntity.ok(legalDocumentService.listAllActive());
    }

    @GetMapping("/{type}")
    public ResponseEntity<LegalDocumentResponseDTO> getActive(@PathVariable LegalDocumentType type) {
        return ResponseEntity.ok(legalDocumentService.getActive(type));
    }
}
