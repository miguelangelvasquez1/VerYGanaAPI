package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.PricingConfig;

@Repository
public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
    
    PricingConfig findFirstByTypeAndActiveTrueOrderByCreatedAtDesc(PricingConfig.PricingType type);
}
