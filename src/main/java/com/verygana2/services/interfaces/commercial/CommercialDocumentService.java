package com.verygana2.services.interfaces.commercial;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentsStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadRequestDTO;

/** Paso 8 - Carga documental. */
public interface CommercialDocumentService {

    DocumentUploadPermissionDTO prepareUpload(Long userId, DocumentUploadRequestDTO dto);

    CommercialDocumentsStatusResponseDTO confirmUpload(Long userId, Long documentId);

    CommercialDocumentsStatusResponseDTO discard(Long userId, Long documentId);

    CommercialDocumentsStatusResponseDTO getStatus(Long userId);

    /**
     * El comercial confirma que la carga documental está completa y avanza a
     * CONTRACT_PENDING. Es una acción explícita (no un side-effect de subir/descartar
     * un documento) porque si ya estaba completa de un ciclo anterior — p. ej. tras
     * un contract/request-changes donde no hace falta tocar ningún documento — nada
     * más recalcula el avance de paso.
     */
    CommercialDocumentsStatusResponseDTO continueToContract(Long userId);
}
