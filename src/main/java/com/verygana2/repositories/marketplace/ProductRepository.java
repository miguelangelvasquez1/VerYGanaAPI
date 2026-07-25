package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.Municipality;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.marketplace.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

        @Query("""
                        SELECT COUNT (p) FROM Product p
                        WHERE p.commercial.id = :commercialId
                        AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                                """)
        long countByCommercialIdAndIsActive(@Param("commercialId") Long commercialId);

        boolean existsByIdAndCommercialId(Long id, Long commercialId);

        boolean existsByProductCategoryId(Long productCategoryId);

        Optional<Product> findByIdAndCommercialId(Long productId, Long commercialId);

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        LEFT JOIN FETCH p.imageAsset ia
                        WHERE p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                                """)
        Page<Product> findAllActiveProducts(Pageable pageable);

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory pc
                        JOIN FETCH p.commercial c
                        LEFT JOIN FETCH p.imageAsset ia
                        WHERE p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                        AND (:searchQuery IS NULL OR :searchQuery = '' OR
                                LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
                                LOWER(p.description) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
                                LOWER(c.companyName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
                                LOWER(pc.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')))
                        AND (:productCategoryId IS NULL OR pc.id = :productCategoryId)
                        AND (:minRating IS NULL OR p.averageRate >= :minRating)
                        AND (:maxPriceCents IS NULL OR p.priceCents <= :maxPriceCents)
                        ORDER BY CASE WHEN (:municipality IS NULL
                                        OR p.targetAudience IS NULL
                                        OR p.targetAudience.targetMunicipalities IS EMPTY
                                        OR :municipality MEMBER OF p.targetAudience.targetMunicipalities)
                                THEN 0 ELSE 1 END ASC
                                """)
        Page<Product> searchProductsInternal(
                        @Param("searchQuery") String searchQuery,
                        @Param("productCategoryId") Long productCategoryId,
                        @Param("minRating") Double minRating,
                        @Param("maxPriceCents") Long maxPriceCents,
                        @Param("municipality") Municipality municipality,
                        Pageable pageable);

        /**
         * El municipio solo prioriza (ordena primero) los productos afines a la
         * localidad del consumidor; nunca excluye resultados, a diferencia del
         * filtrado de rifas. Ver TargetAudienceAssembler / plan de sectorización.
         */
        default Page<Product> searchProducts(
                        String searchQuery,
                        Long productCategoryId,
                        Double minRating,
                        Long maxPriceCents,
                        Municipality municipality,
                        Pageable pageable) {
                return searchProductsInternal(searchQuery, productCategoryId, minRating, maxPriceCents, municipality,
                                pageable);
        }

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        WHERE p.commercial.id = :commercialId AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                        """)
        Page<Product> findByCommercialId(@Param("commercialId") Long commercialId, Pageable pageable);

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        WHERE p.commercial.id = :commercialId AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                        """)
        List<Product> findByCommercialId(@Param("commercialId") Long commercialId);

        @Query("""
                        SELECT p FROM Product p
                        WHERE p.commercial.id = :commercialId
                        AND p.isGameReward = TRUE
                        AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                        ORDER BY p.name DESC
                        LIMIT 3
                                """)
        List<Product> findGameRewardsProducts(@Param("commercialId") Long commercialId);

        @Query("""
                        SELECT COUNT(p) FROM Product p
                        WHERE p.commercial.id = :commercialId
                        AND p.isGameReward = TRUE
                        AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
                                """)
        Integer countGameRewards(@Param("commercialId") Long commercialId);

        @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.commercial.id = :commercialId")
        Integer countCommercialProducts(@Param("commercialId") Long commercialId,
                        @Param("status") ProductStatus status);

        @Query("""
                        SELECT p FROM Product p
                        JOIN FETCH p.productCategory c
                        LEFT JOIN FETCH p.imageAsset ia
                        WHERE (:status IS NULL OR p.status = :status)
                        AND (:search IS NULL OR :search = ''
                        OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(p.commercial.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
                        ORDER BY p.createdAt DESC
                                """)
        Page<Product> findAllProductsForAdmin(@Param("status") ProductStatus status, @Param("search") String search, Pageable pageable);

        /**
         * Candidatos a purga semanal: productos REJECTED/INACTIVE que llevan al
         * menos {@code threshold} sin cambiar de estado (updatedAt). Se purgan
         * incluso si tuvieron compras reales: ProductServiceImpl.purgeProduct
         * desvincula (nunca borra) los PurchaseItem asociados antes de borrar
         * el producto, así que el historial financiero nunca se pierde.
         */
        @Query("""
                        SELECT p FROM Product p
                        WHERE p.status IN (
                                com.verygana2.models.enums.marketplace.ProductStatus.REJECTED,
                                com.verygana2.models.enums.marketplace.ProductStatus.INACTIVE)
                        AND p.updatedAt < :threshold
                                """)
        List<Product> findPurgeableProducts(@Param("threshold") ZonedDateTime threshold);

}
