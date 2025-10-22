package com.verygana2.repositories;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.products.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByIdAndSellerId(Long id, Long sellerId);

    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.category c " +
            "WHERE p.isActive = true")
    Page<Product> findAllActiveProducts(Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.category c " +
            "WHERE p.isActive = true " +
            "AND (:searchQuery IS NULL OR :searchQuery = '' OR " +
            "     LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR " +
            "     LOWER(p.description) LIKE LOWER(CONCAT('%', :searchQuery, '%'))) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:minRating IS NULL OR p.averageRate >= :minRating) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchProducts(
            @Param("searchQuery") String searchQuery,
            @Param("categoryId") Long categoryId,
            @Param("minRating") Double minRating,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

}
