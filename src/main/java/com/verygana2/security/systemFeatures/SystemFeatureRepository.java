package com.verygana2.security.systemFeatures;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemFeatureRepository extends JpaRepository<SystemFeature, Long> {

    Optional<SystemFeature> findByFeatureKey(String key);
}