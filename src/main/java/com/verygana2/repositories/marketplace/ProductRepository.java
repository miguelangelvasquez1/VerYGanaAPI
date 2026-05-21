package com.verygana2.repositories.marketplace;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.marketplace.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

        default long countByCommercialIdAndIsActiveTrue(Long commercialId) {
                return countCommercialProducts(commercialId, ProductStatus.ACTIVE);
        }

        @Query("""
                        SELECT COUNT (p) FROM Product p
                        WHERE p.commercial.id = :commercialId
                        AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                                """)
        long countByCommercialIdAndIsActive(@Param("commercialId") Long commercialId);

        boolean existsByIdAndCommercialId(Long id, Long commercialId);

        Optional<Product> findByIdAndCommercialId(Long productId, Long commercialId);

        @Query("SELECT p FROM Product p " +
                        "JOIN FETCH p.productCategory c " +
                        "WHERE p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE")
        Page<Product> findAllActiveProducts(Pageable pageable);

        @Query("SELECT p FROM Product p " +
                        "JOIN FETCH p.productCategory c " +
                        "WHERE p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE " +
                        "AND (:searchQuery IS NULL OR :searchQuery = '' OR " +
                        "     LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR " +
                        "     LOWER(p.description) LIKE LOWER(CONCAT('%', :searchQuery, '%'))) " +
                        "AND (:productCategoryId IS NULL OR c.id = :productCategoryId) " +
                        "AND (:minRating IS NULL OR p.averageRate >= :minRating) " +
                        "AND (:maxPriceCents IS NULL OR p.priceCents <= :maxPriceCents)")
        Page<Product> searchProductsInternal(
                        @Param("searchQuery") String searchQuery,
                        @Param("productCategoryId") Long productCategoryId,
                        @Param("minRating") Double minRating,
                        @Param("maxPriceCents") Long maxPriceCents,
                        Pageable pageable);

        default Page<Product> searchProducts(
                        String searchQuery,
                        Long productCategoryId,
                        Double minRating,
                        BigDecimal maxPrice,
                        Pageable pageable) {
                Long maxPriceCents = maxPrice != null
                                ? maxPrice.multiply(BigDecimal.valueOf(100)).longValue()
                                : null;
                return searchProductsInternal(searchQuery, productCategoryId, minRating, maxPriceCents, pageable);
        }

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        WHERE p.commercial.id = :commercialId AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                        """)
        Page<Product> findByCommercialId(@Param("commercialId") Long commercialId, Pageable pageable);

        @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.commercial.id = :commercialId")
        Long countCommercialProducts(@Param("commercialId") Long commercialId, @Param("status") ProductStatus status);

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        LEFT JOIN FETCH p.imageAsset ia
                        WHERE p.status = :status
                        ORDER BY p.createdAt DESC
                                """)
        Page<Product> getAllProductsForAdmin(@Param("status") ProductStatus status, Pageable pageable);

}
