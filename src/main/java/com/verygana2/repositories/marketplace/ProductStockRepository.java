package com.verygana2.repositories.marketplace;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.ProductStock;

import jakarta.persistence.LockModeType;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.id = :id")
    Optional<ProductStock> findByIdWithLock(@Param("id") Long id);

    // FOR UPDATE SKIP LOCKED: evita que dos transacciones concurrentes tomen el mismo stock.
    // NOT EXISTS en purchase_items: protege ante inconsistencias donde el stock dice AVAILABLE
    // pero ya tiene un PurchaseItem activo apuntando a él.
    @Query(value = """
            SELECT ps.* FROM product_stock ps
            WHERE ps.product_id = :productId
              AND ps.status = 'AVAILABLE'
              AND (ps.expiration_date IS NULL OR ps.expiration_date > NOW())
              AND NOT EXISTS (
                  SELECT 1 FROM purchase_items pi
                  WHERE pi.product_stock_id = ps.id
                    AND pi.status != 'CANCELLED'
              )
            ORDER BY ps.id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
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
           "AND ps.product.commercial.id = :commercialId")
    Optional<ProductStock> findByIdAndProductIdAndProductCommercialId(@Param("stockId") Long stockId, @Param("productId") Long productId, @Param("commercialId") Long commercialId);
    
    boolean existsByProductIdAndCode(Long productId, String code);
    
    @Query("SELECT COUNT(ps) FROM ProductStock ps WHERE ps.product.id = :productId AND ps.status = :status")
    Integer countByProductIdAndStatus(@Param("productId") Long productId, @Param("status") StockStatus status);
}

