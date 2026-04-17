package com.verygana2.repositories.plans;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.Subscription;
import com.verygana2.models.plans.Subscription.SubscriptionStatus;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByCommercialDetailsIdAndStatus(Long commercialId, SubscriptionStatus status);

    boolean existsByCommercialDetailsIdAndStatus(Long commercialId, SubscriptionStatus status);

    @Query("""
    SELECT s FROM Subscription s
    WHERE s.commercialDetails.id = :commercialId
    AND s.status = 'ACTIVE'
        """)
        Optional<Subscription> findActiveByCommercialId(Long commercialId);
}