package com.verygana2.repositories.legal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.legal.LegalDocument;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {
    Optional<LegalDocument> findByTypeAndActiveTrue(LegalDocumentType type);
    Optional<LegalDocument> findByTypeAndVersion(LegalDocumentType type, String version);
    List<LegalDocument> findByTypeAndStatusOrderByPublishedDateDesc(LegalDocumentType type, LegalDocumentStatus status);
    List<LegalDocument> findByTypeAndStatusOrderByCreatedAtAsc(LegalDocumentType type, LegalDocumentStatus status);

    /** Última fila creada para ese tipo, sin importar el estado — usada para calcular la siguiente versión. */
    Optional<LegalDocument> findFirstByTypeOrderByCreatedAtDesc(LegalDocumentType type);
}
