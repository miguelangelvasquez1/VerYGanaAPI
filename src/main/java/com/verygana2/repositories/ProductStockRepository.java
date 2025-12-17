package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.products.ProductStock;

import jakarta.persistence.LockModeType;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.id = :id")
    Optional<ProductStock> findByIdWithLock(@Param("id") Long id);

    // En ProductStockRepository
    @Query(value = "SELECT * FROM product_stock WHERE product_id = :productId AND status = 'AVAILABLE' AND (expiration_date IS NULL OR expiration_date > NOW()) ORDER BY id ASC LIMIT 1", nativeQuery = true)
    Optional<ProductStock> findNextAvailableForProduct(@Param("productId") Long productId);
}
