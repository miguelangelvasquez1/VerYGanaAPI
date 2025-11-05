package com.verygana2.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.products.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>{
    Page<Purchase> findByConsumerId(Long consumerId, Pageable pageable);
}
