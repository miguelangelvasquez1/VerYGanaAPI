package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.products.FavoriteProduct;

public interface FavoriteProductRepository extends JpaRepository<FavoriteProduct, Long> {

    @Query("""
            SELECT fp FROM FavoriteProduct fp
            JOIN FETCH fp.product p
            WHERE fp.consumer.id = :consumerId
            AND p.isActive = true
            ORDER BY fp.createdAt DESC
            """)
    Page<FavoriteProduct> findByConsumerIdWithActiveProducts(
            @Param("consumerId") Long consumerId,
            Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(fp) > 0 THEN true ELSE false END " +
            "FROM FavoriteProduct fp " +
            "WHERE fp.consumer.id = :consumerId " +
            "AND fp.product.id = :productId")
    boolean existsByConsumerIdAndProductId(
            @Param("consumerId") Long consumerId,
            @Param("productId") Long productId);

    @Query("""
            SELECT fp FROM FavoriteProduct fp
            WHERE fp.consumer.id = :consumerId
            AND fp.product.id = :productId
            """)
    Optional<FavoriteProduct> findByConsumerIdAndProductId(
            @Param("consumerId") Long consumerId,
            @Param("productId") Long productId);

    @Query("""
            SELECT COUNT(fp) FROM FavoriteProduct fp
            JOIN fp.product p
            WHERE fp.consumer.id = :consumerId
            AND p.isActive = true
            """)
    Long countByConsumerId(@Param("consumerId") Long consumerId);
}
