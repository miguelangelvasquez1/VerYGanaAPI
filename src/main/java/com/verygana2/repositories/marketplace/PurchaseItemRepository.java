package com.verygana2.repositories.marketplace;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.models.marketplace.PurchaseItem;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    @Query("""
                SELECT pi FROM PurchaseItem pi
                LEFT JOIN FETCH pi.product p
                LEFT JOIN FETCH pi.review r
                WHERE pi.purchase.consumer.id = :consumerId
                AND pi.status = 'DELIVERED'
                AND pi.review IS NULL
                ORDER BY pi.deliveredAt DESC
            """)
    List<PurchaseItem> findDeliveredItemsWithoutReview(@Param("consumerId") Long consumerId);

    @Query("""
                SELECT CASE WHEN COUNT(pi) > 0 THEN true ELSE false END
                FROM PurchaseItem pi
                WHERE pi.id = :purchaseItemId
                AND pi.purchase.consumer.id = :consumerId
                AND pi.status = 'DELIVERED'
                AND pi.review IS NULL
            """)
    boolean canUserReviewPurchaseItem(
            @Param("purchaseItemId") Long purchaseItemId,
            @Param("consumerId") Long consumerId);

    @Query("""
                SELECT pi FROM PurchaseItem pi
                LEFT JOIN FETCH pi.product
                LEFT JOIN FETCH pi.purchase p
                WHERE pi.id = :purchaseItemId
                AND p.consumer.id = :consumerId
            """)
    Optional<PurchaseItem> findByIdAndConsumerId(
            @Param("purchaseItemId") Long purchaseItemId,
            @Param("consumerId") Long consumerId);

    // Bug fix: PurchaseItem has no quantity field — each item = 1 unit
    // Usa el snapshot commercialId (no p.product.commercial.id): el producto puede
    // haberse purgado y este conteo histórico debe seguir siendo correcto.
    @Query("SELECT COUNT(p) FROM PurchaseItem p WHERE p.commercialId = :commercialId")
    Long countTotalSalesByCommercialId(@Param("commercialId") Long commercialId);

    @Query("""
           SELECT COUNT(p) FROM PurchaseItem p WHERE p.commercialId = :commercialId
           AND p.deliveredAt >= :startDate
           AND p.deliveredAt < :endDate     
                    """)
    Integer countTotalSalesByCommercialIdAndDatesRange (@Param("commercialId") Long commercialId, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    // Internal query returns Long cents; public default method converts to BigDecimal pesos
    @Query("""
            SELECT SUM(p.subtotalCents)
            FROM PurchaseItem p
            WHERE p.commercialId = :commercialId
            AND p.deliveredAt >= :startDate
            AND p.deliveredAt < :endDate
            """)
    Long sumTotalCommercialSalesAmountByMonthCents(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

    default BigDecimal sumTotalCommercialSalesAmountByMonth(Long commercialId,
            ZonedDateTime startDate, ZonedDateTime endDate) {
        Long cents = sumTotalCommercialSalesAmountByMonthCents(commercialId, startDate, endDate);
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));
    }

    @Query("""
            SELECT COUNT(p)
            FROM PurchaseItem p
            WHERE p.commercialId = :commercialId
            AND p.deliveredAt >= :startDate
            AND p.deliveredAt < :endDate
            """)
    Integer findTotalCommercialSalesByMonth(@Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

    // Bug fix: use commissionCents directly (already calculated);
    // original JPQL did integer division subtotalCents*(pct/100) which always truncated to 0
    @Query("""
            SELECT SUM(p.commissionCents)
            FROM PurchaseItem p
            WHERE p.commercialId = :commercialId
            AND p.deliveredAt >= :startDate
            AND p.deliveredAt < :endDate
            """)
    Long sumTotalPlatformCommissionsByMonthCents(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

    default BigDecimal sumTotalPlatformCommissionsByMonth(Long commercialId,
            ZonedDateTime startDate, ZonedDateTime endDate) {
        Long cents = sumTotalPlatformCommissionsByMonthCents(commercialId, startDate, endDate);
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));
    }

    // Bug fix: p.price → p.priceCents (Long); FeaturedProductResponseDTO has a
    // matching constructor that converts cents to BigDecimal
    @Query("""
            SELECT new com.verygana2.dtos.product.responses.FeaturedProductResponseDTO(
                p.id, p.name, ia.objectKey, p.priceCents,
                COALESCE(p.averageRate, 0.0),
                COUNT(pi.id))
            FROM PurchaseItem pi
            JOIN pi.product p
            JOIN p.imageAsset ia
            WHERE p.commercial.id = :commercialId
            AND p.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
            GROUP BY p.id, p.name, ia.objectKey, p.priceCents, p.averageRate, p.status
            ORDER BY COUNT(pi.id) DESC
            """)
    Page<FeaturedProductResponseDTO> findTopSellingProducts(
            @Param("commercialId") Long commercialId,
            Pageable pageable);

    // A diferencia de findTopSellingProducts (catálogo actual), este reporte es histórico:
    // no filtra por p.status = ACTIVE porque un producto ya inactivo/descontinuado debe
    // seguir apareciendo en el reporte del período en que sí vendió.
    @Query("""
            SELECT new com.verygana2.dtos.product.responses.FeaturedProductResponseDTO(
                p.id, p.name, ia.objectKey, p.priceCents,
                COALESCE(p.averageRate, 0.0),
                COUNT(pi.id))
            FROM PurchaseItem pi
            JOIN pi.product p
            JOIN p.imageAsset ia
            WHERE p.commercial.id = :commercialId
            AND pi.deliveredAt >= :startDate
            AND pi.deliveredAt < :endDate
            GROUP BY p.id, p.name, ia.objectKey, p.priceCents, p.averageRate
            ORDER BY COUNT(pi.id) DESC
            """)
    Page<FeaturedProductResponseDTO> findTopSellingProductsByDateRange(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate,
            Pageable pageable);

    // Listado transaccional día a día (no agregado) para el panel de ventas.
    // Usa pi.commercialId (snapshot) y COALESCE con productNameSnapshot por la misma
    // razón que el resto de reportes históricos de este repositorio: debe seguir
    // siendo legible aunque el producto ya se haya purgado.
    @Query("""
            SELECT new com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO(
                pi.id,
                COALESCE(p.name, pi.productNameSnapshot),
                pi.deliveredAt,
                pi.subtotalCents,
                pi.commissionCents,
                pi.netToCommercialCents)
            FROM PurchaseItem pi
            LEFT JOIN pi.product p
            WHERE pi.commercialId = :commercialId
            AND pi.deliveredAt >= :startDate
            AND pi.deliveredAt < :endDate
            ORDER BY pi.deliveredAt DESC
            """)
    Page<DailySaleResponseDTO> findDailySalesByCommercialAndDateRange(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate,
            Pageable pageable);

    /**
     * Desvincula (nunca borra) los PurchaseItem de un producto que se va a
     * purgar permanentemente. product y assignedProductStock quedan en null;
     * unitPriceCents, commissionCents, productNameSnapshot, commercialId,
     * etc. se conservan intactos como historial financiero/auditable.
     * Ver ProductServiceImpl.purgeProduct.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PurchaseItem pi
            SET pi.product = NULL, pi.assignedProductStock = NULL
            WHERE pi.product.id = :productId
            """)
    void detachProductReferences(@Param("productId") Long productId);

    // ── Panel de inicio del comercial (CommercialDashboardService) ────────────

    /** Ganancia neta del comercial (subtotal - comisión) en el rango; null si no hubo ventas. */
    @Query("""
            SELECT SUM(p.netToCommercialCents)
            FROM PurchaseItem p
            WHERE p.commercialId = :commercialId
            AND p.deliveredAt >= :startDate
            AND p.deliveredAt < :endDate
            """)
    Long sumNetToCommercialCents(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

    /**
     * Instantes de entrega + subtotal de cada venta DELIVERED del rango, sin agregar.
     * El servicio los agrupa por día natural en zona local para la serie de tendencia
     * (rango acotado: máx. ~31 días de un solo comercial).
     * Cada fila: {@code [deliveredAt (temporal), subtotalCents (Number)]}.
     */
    @Query("""
            SELECT p.deliveredAt, p.subtotalCents
            FROM PurchaseItem p
            WHERE p.commercialId = :commercialId
            AND p.status = 'DELIVERED'
            AND p.deliveredAt >= :startDate
            AND p.deliveredAt < :endDate
            """)
    List<Object[]> findDeliveredSaleInstants(
            @Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

}
