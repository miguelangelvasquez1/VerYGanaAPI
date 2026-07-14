package com.verygana2.repositories.finance.plans;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.Feature;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {
    
}