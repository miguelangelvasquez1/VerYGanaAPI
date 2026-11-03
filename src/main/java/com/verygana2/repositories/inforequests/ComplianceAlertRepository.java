package com.verygana2.repositories.inforequests;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.inforequests.ComplianceAlert;

public interface ComplianceAlertRepository extends JpaRepository<ComplianceAlert, Long> {

    Page<ComplianceAlert> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<ComplianceAlert> findByReviewedAtIsNullOrderByDetectedAtDesc(Pageable pageable);

    /** Hay una alerta sin revisar de esta regla para el empresario: no se repite cada día. */
    boolean existsByRule_CodeAndCommercial_IdAndReviewedAtIsNull(String ruleCode, Long commercialId);
}
