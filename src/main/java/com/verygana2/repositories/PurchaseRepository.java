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
            SELECT DISTINCT p FROM Purchase p
            LEFT JOIN FETCH p.consumer consumer
            LEFT JOIN FETCH consumer.user
            LEFT JOIN FETCH p.items items
            LEFT JOIN FETCH items.product product
            LEFT JOIN FETCH items.assignedProductStock stock
            WHERE p.id = :purchaseId
            AND p.consumer.id = :consumerId
                """)
    Optional<Purchase> findByIdAndConsumerIdWithItems(@Param("purchaseId") Long purchaseId,
            @Param("consumerId") Long consumerId);

    Page<Purchase> findByConsumerId(Long consumerId, Pageable pageable);

    Optional<Purchase> findByReferenceId(String referenceId);
}
