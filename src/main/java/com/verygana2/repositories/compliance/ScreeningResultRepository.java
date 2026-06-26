package com.verygana2.repositories.compliance;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.compliance.ScreeningResult;
import com.verygana2.models.enums.ScreeningStatus;

@Repository
public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {

    List<ScreeningResult> findByUserId(Long userId);

    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.status IN (:statuses) AND sr.reviewedByOfficerId IS NULL ORDER BY sr.createdAt DESC")
    Page<ScreeningResult> findUnresolvedHits(@Param("statuses") List<ScreeningStatus> statuses, Pageable pageable);

    boolean existsByUserIdAndStatusIn(Long userId, List<ScreeningStatus> statuses);
}