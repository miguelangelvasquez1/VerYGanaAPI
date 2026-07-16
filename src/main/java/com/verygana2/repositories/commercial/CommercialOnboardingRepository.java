package com.verygana2.repositories.commercial;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.commercial.CommercialOnboarding;

public interface CommercialOnboardingRepository extends JpaRepository<CommercialOnboarding, Long> {
    Optional<CommercialOnboarding> findByUser_Id(Long userId);
    boolean existsByUser_Id(Long userId);
}
