package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.products.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>{
    
}
