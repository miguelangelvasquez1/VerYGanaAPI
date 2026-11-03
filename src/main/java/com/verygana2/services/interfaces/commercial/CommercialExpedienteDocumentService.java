package com.verygana2.services.interfaces.commercial;

import java.util.List;
import java.util.UUID;

import com.verygana2.dtos.user.commercial.expediente.DocumentReviewRequestDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentUploadRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;

/**
 * Expediente documental del empresario (4.7 / 4.8): documentos con historial, vigencia,
 * titular y revisión de contenido. Las cargas del onboarding siguen en
 * {@link CommercialDocumentService}; aquí entran las actualizaciones posteriores.
 */
public interface CommercialExpedienteDocumentService {

    List<ExpedienteDocumentDTO> listForCommercial(Long commercialId);

    DocumentUploadPermissionDTO prepareUpload(Long commercialId, ExpedienteDocumentUploadRequestDTO dto);

    ExpedienteDocumentDTO confirmUpload(Long commercialId, Long documentId);

    List<ExpedienteDocumentDTO> listForCompliance(UUID commercialPublicId);

    ExpedienteDocumentDTO review(Long documentId, Long reviewerUserId, DocumentReviewRequestDTO dto);
}
