package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.StockStatus;
import com.verygana2.models.products.ProductStock;

import jakarta.persistence.LockModeType;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.id = :id")
    Optional<ProductStock> findByIdWithLock(@Param("id") Long id);

    // En ProductStockRepository
    @Query(value = "SELECT * FROM product_stock WHERE product_id = :productId AND status = 'AVAILABLE' AND (expiration_date IS NULL OR expiration_date > NOW()) ORDER BY id ASC LIMIT 1", nativeQuery = true)
    Optional<ProductStock> findNextAvailableForProduct(@Param("productId") Long productId);

    Page<ProductStock> findByProductId(Long productId, Pageable pageable);
    
    Page<ProductStock> findByProductIdAndCodeContainingIgnoreCase(
        Long productId, String code, Pageable pageable
    );
    
    Page<ProductStock> findByProductIdAndStatus(
        Long productId, StockStatus status, Pageable pageable
    );
    
    Page<ProductStock> findByProductIdAndCodeContainingIgnoreCaseAndStatus(
        Long productId, String code, StockStatus status, Pageable pageable
    );

    @Query("SELECT ps FROM ProductStock ps " +
           "WHERE ps.id = :stockId " +
           "AND ps.product.id = :productId " +
           "AND ps.product.seller.id = :sellerId")
    Optional<ProductStock> findByIdAndProductIdAndProductSellerId(@Param("stockId") Long stockId, @Param("productId") Long productId, @Param("sellerId") Long sellerId);
    
    boolean existsByProductIdAndCode(Long productId, String code);
    
    @Query("SELECT COUNT(ps) FROM ProductStock ps WHERE ps.product.id = :productId AND ps.status = :status")
    Integer countByProductIdAndStatus(@Param("productId") Long productId, @Param("status") StockStatus status);
}

