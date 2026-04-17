package com.verygana2.repositories.plans;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.Plan.PlanCode;
import com.verygana2.models.plans.PlanFeature;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    List<PlanFeature> findByPlanCode(PlanCode planCode);

    Optional<PlanFeature> findByPlanCodeAndFeatureCode(PlanCode planCode, String featureCode);
}