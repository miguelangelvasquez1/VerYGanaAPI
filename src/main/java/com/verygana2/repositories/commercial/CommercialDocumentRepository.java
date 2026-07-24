package com.verygana2.repositories.commercial;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;

public interface CommercialDocumentRepository extends JpaRepository<CommercialDocument, Long> {
    List<CommercialDocument> findByOnboarding_IdAndStatus(Long onboardingId, CommercialDocumentStatus status);
    List<CommercialDocument> findByOnboarding_IdAndStatusNot(Long onboardingId, CommercialDocumentStatus status);
    Optional<CommercialDocument> findByIdAndOnboarding_Id(Long id, Long onboardingId);
}
