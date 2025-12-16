package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.products.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("""
                SELECT p FROM Purchase p
                JOIN FETCH p.items
                WHERE p.id = :purchaseId
                AND p.consumer.id = :consumerId
            """)
    Optional<Purchase> findByIdAndConsumerIdWithItems(@Param("purchaseId") Long purchaseId,
            @Param("consumerId") Long consumerId);

    Page<Purchase> findByConsumerId(Long consumerId, Pageable pageable);

    Optional<Purchase> findByReferenceId(String referenceId);
}
