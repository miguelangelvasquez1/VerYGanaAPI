package com.verygana2.repositories.finance.plans;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    
    Optional<Plan> findByCodeAndActiveTrue(PlanCode code);

    @Query("""
        SELECT p FROM Plan p
        WHERE p.active = true
        AND p.code <> 'BASIC'
        AND (p.minInvestmentCents IS NULL OR p.minInvestmentCents <= :amount)
        AND (p.maxInvestmentCents IS NULL OR p.maxInvestmentCents >= :amount)
        ORDER BY p.minInvestmentCents DESC
    """)
    List<Plan> findEligiblePlans(BigDecimal amount);
}