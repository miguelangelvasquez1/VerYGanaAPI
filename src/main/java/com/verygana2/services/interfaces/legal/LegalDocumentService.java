package com.verygana2.services.interfaces.legal;

import java.util.List;

import com.verygana2.dtos.legal.LegalDocumentPrepareUploadRequestDTO;
import com.verygana2.dtos.legal.LegalDocumentResponseDTO;
import com.verygana2.dtos.legal.LegalDocumentUploadPermissionDTO;
import com.verygana2.models.enums.legal.LegalDocumentType;

public interface LegalDocumentService {

    LegalDocumentResponseDTO getActive(LegalDocumentType type);

    List<LegalDocumentResponseDTO> listAllActive();

    List<LegalDocumentResponseDTO> listHistory(LegalDocumentType type);

    /** Paso 1: crea la fila PENDING y devuelve la URL pre-firmada para subir el PDF a R2. */
    LegalDocumentUploadPermissionDTO prepareUpload(LegalDocumentPrepareUploadRequestDTO dto);

    /**
     * Paso 2: valida lo subido a R2 (tamaño/mime real), publica la versión (desactiva
     * la anterior del mismo tipo) y aplica la retención (máx. 10 documentos por tipo).
     */
    LegalDocumentResponseDTO confirmUpload(Long documentId);

    /** Descarta una subida pendiente (cancelada o reemplazada antes de confirmar). */
    void discardUpload(Long documentId);
}
