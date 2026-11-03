package com.verygana2.repositories.inforequests;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.inforequests.ComplianceFinding;

public interface ComplianceFindingRepository extends JpaRepository<ComplianceFinding, Long> {

    List<ComplianceFinding> findByCommercial_IdOrderByDecidedAtDesc(Long commercialId);
}
