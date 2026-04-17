package com.verygana2.repositories.plans;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.Plan;
import com.verygana2.models.plans.Plan.PlanCode;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    
    Optional<Plan> findByCode(PlanCode code);

    @Query("""
        SELECT p FROM Plan p
        WHERE p.active = true
        AND p.code <> 'BASIC'
        AND (p.minInvestment IS NULL OR p.minInvestment <= :amount)
        AND (p.maxInvestment IS NULL OR p.maxInvestment >= :amount)
        ORDER BY p.minInvestment DESC
    """)
    List<Plan> findEligiblePlans(BigDecimal amount);
}