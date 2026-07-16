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
}
