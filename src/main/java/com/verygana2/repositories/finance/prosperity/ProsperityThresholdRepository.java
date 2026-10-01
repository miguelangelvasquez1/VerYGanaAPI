package com.verygana2.repositories.finance.prosperity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.prosperity.ProsperityThreshold;

@Repository
public interface ProsperityThresholdRepository extends JpaRepository<ProsperityThreshold, Long> {

    Optional<ProsperityThreshold> findByInvestmentId(Long investmentId);

    boolean existsByInvestmentId(Long investmentId);

    List<ProsperityThreshold> findByAccountIdOrderByValidatedAtAsc(Long accountId);

    @Query("SELECT COALESCE(SUM(t.generatedCents), 0) FROM ProsperityThreshold t WHERE t.account.id = :accountId")
    long sumGeneratedByAccountId(@Param("accountId") Long accountId);
}
