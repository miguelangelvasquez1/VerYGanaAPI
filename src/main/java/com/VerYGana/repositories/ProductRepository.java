package com.VerYGana.repositories;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.VerYGana.models.marketplace.Product;

public interface ProductRepository extends JpaRepository<Product, Long>{

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
    
    Page<Product> findByAverageRateGreaterThanEqualAndIsActiveTrue(Double averageRate, Pageable pageable);

    Page<Product> findByPriceLessThanEqualAndIsActiveTrue(BigDecimal price, Pageable pageable);

    Page<Product> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    
    Page<Product> findBySellerIdAndIsActiveTrue(Long sellerId, Pageable pageable);
    
}
