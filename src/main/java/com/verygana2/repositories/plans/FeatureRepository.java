package com.verygana2.repositories.plans;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.Feature;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {
    
    Optional<Feature> findByCode(String code);
}