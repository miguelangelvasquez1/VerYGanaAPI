package com.verygana2.repositories.surveys;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.surveys.SurveyReward;

@Repository
public interface SurveyRewardRepository extends JpaRepository<SurveyReward, Long> {
 
    Page<SurveyReward> findByUserId(Long userId, Pageable pageable);
 
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM SurveyReward r WHERE r.userId = :userId AND r.status = 'PROCESSED'")
    BigDecimal getTotalRewardsByUser(@Param("userId") Long userId);
}
 
