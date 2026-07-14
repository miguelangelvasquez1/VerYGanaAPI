package com.verygana2.repositories.finance.plans;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.finance.plans.Plan.PlanCode;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    Optional<PlanFeature> findByPlanCodeAndFeatureCode(PlanCode planCode, String featureCode);
}