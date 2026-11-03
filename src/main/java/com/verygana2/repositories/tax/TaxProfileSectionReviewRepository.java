package com.verygana2.repositories.tax;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.tax.TaxProfileSectionReview;

public interface TaxProfileSectionReviewRepository extends JpaRepository<TaxProfileSectionReview, Long> {

    /** Todas las revisiones de la versión, la más reciente primero. */
    List<TaxProfileSectionReview> findByVersion_IdOrderByReviewedAtDescIdDesc(Long versionId);
}
