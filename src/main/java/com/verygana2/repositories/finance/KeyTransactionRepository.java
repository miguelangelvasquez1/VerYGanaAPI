package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.models.finance.KeyTransaction;

@Repository
public interface KeyTransactionRepository extends JpaRepository<KeyTransaction, UUID> {

    @Query("""
            SELECT kt FROM KeyTransaction kt 
            WHERE kt.keyWallet.consumer.id = :consumerId 
            AND (:initialDate IS NULL OR kt.createdAt >= :initialDate)
            AND (:endDate IS NULL OR kt.createdAt <= :endDate)
            AND (:type IS NULL OR kt.type = :type) 
            ORDER BY kt.createdAt DESC
            """)
    Page<KeyTransaction> findByConsumerId(@Param("consumerId") Long consumerId, @Param ("initialDate") ZonedDateTime initialDate, @Param ("endDate") ZonedDateTime endDate, @Param("type") KeyTransactionType type, Pageable pageable);

    @Query("""
            SELECT SUM(COALESCE(kt.purchaseKeysDeltaCents, 0) + COALESCE(kt.connectivityKeysDeltaCents, 0))
            FROM KeyTransaction kt
            JOIN kt.keyWallet kw
            WHERE kw.consumer.id = :consumerId
            AND kt.type IN (
                com.verygana2.models.enums.finance.KeyTransactionType.CREDIT_INTERACTION,
                com.verygana2.models.enums.finance.KeyTransactionType.CREDIT_REFERRAL_BONUS,
                com.verygana2.models.enums.finance.KeyTransactionType.CREDIT_ADMIN_ADJUSTMENT
            )
            """)
    Long sumTotalEarnedKeysCents(@Param("consumerId") Long consumerId);

    @Query("""
            SELECT SUM(COALESCE(kt.purchaseKeysDeltaCents, 0) + COALESCE(kt.connectivityKeysDeltaCents, 0))
            FROM KeyTransaction kt
            JOIN kt.keyWallet kw
            WHERE kw.consumer.id = :consumerId
            AND kt.type IN (
                com.verygana2.models.enums.finance.KeyTransactionType.DEBIT_COPAYMENT,
                com.verygana2.models.enums.finance.KeyTransactionType.DEBIT_CONNECTIVITY_RECHARGE,
                com.verygana2.models.enums.finance.KeyTransactionType.DEBIT_ADMIN_ADJUSTMENT
            )
            """)
    Long sumTotalUsedKeysCents(@Param("consumerId") Long consumerId);

    @Query("""
            SELECT SUM(COALESCE(kt.purchaseKeysDeltaCents, 0) + COALESCE(kt.connectivityKeysDeltaCents, 0))
            FROM KeyTransaction kt
            JOIN kt.keyWallet kw
            WHERE kw.consumer.id = :consumerId
            AND kt.type = com.verygana2.models.enums.finance.KeyTransactionType.EXPIRED
            """)
    Long sumTotalExpiredKeysCents(@Param("consumerId") Long consumerId);

    /**
     * Devuelve todos los créditos de llaves que ya vencieron y aún no se procesaron.
     * Solo créditos (delta > 0) para evitar contar débitos y reservas.
     */
    @Query("""
            SELECT kt FROM KeyTransaction kt
            JOIN FETCH kt.keyWallet kw
            WHERE kt.expiredAt IS NOT NULL
            AND kt.expiredAt < :now
            AND kt.expiryProcessed = false
            AND (kt.purchaseKeysDeltaCents > 0 OR kt.connectivityKeysDeltaCents > 0)
            ORDER BY kt.keyWallet.id
            """)
    List<KeyTransaction> findExpiredNotProcessed(@Param("now") ZonedDateTime now);

    /**
     * Ventas por producto de un comercial en el juego de mascotas.
     *
     * Nativa y no JPQL porque {@code petCatalogItemId} se guarda como id suelto —
     * finanzas no depende del modelo de mascotas—, así que no hay asociación que
     * recorrer. El vínculo comercial→producto sale de la solicitud de integración
     * que dio origen al ítem ({@code result_catalog_item_id}).
     *
     * Cuenta solo transacciones con {@code pet_catalog_item_id}: las anteriores a
     * esa columna quedaron rellenadas, y lo que no se pudo casar es ítem borrado.
     *
     * El rango de fechas va en el ON y no en el WHERE a propósito: en el WHERE
     * convertiría el LEFT JOIN en INNER y los productos sin ventas en ese periodo
     * desaparecerían del panel, que es justo lo que no queremos mostrarle al
     * comercial. Con el filtro en el ON salen igual, con los contadores en cero.
     */
    @Query(value = """
            SELECT  i.id                                   AS catalogItemId,
                    i.external_id                          AS externalId,
                    i.name                                 AS productName,
                    i.price                                AS priceKeys,
                    i.active                               AS active,
                    COUNT(t.id)                            AS unitsSold,
                    COALESCE(SUM(-t.purchase_keys_delta_cents), 0) AS revenueCents,
                    COUNT(DISTINCT w.consumer_id)          AS uniqueBuyers,
                    MIN(t.created_at)                      AS firstSale,
                    MAX(t.created_at)                      AS lastSale
            FROM catalog_integration_requests r
            JOIN pet_catalog_items i ON i.id = r.result_catalog_item_id
            LEFT JOIN key_transactions t
                   ON t.pet_catalog_item_id = i.id
                  AND t.type = 'DEBIT_PET_GAME'
                  AND t.created_at >= :from
                  AND t.created_at < :to
            LEFT JOIN key_wallets w ON w.id = t.key_wallet_id
            WHERE r.commercial_id = :commercialId
            GROUP BY i.id, i.external_id, i.name, i.price, i.active
            ORDER BY revenueCents DESC
            """, nativeQuery = true)
    List<PetProductSalesRow> findPetProductSalesByCommercial(
            @Param("commercialId") Long commercialId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /**
     * Ventas por día de todos los productos del comercial, para la gráfica de evolución.
     *
     * Solo devuelve los días con ventas; rellenar los huecos es cosa del servicio,
     * porque una gráfica que se salta los días vacíos miente sobre la tendencia.
     */
    @Query(value = """
            SELECT  DATE(t.created_at)                     AS day,
                    COUNT(*)                               AS unitsSold,
                    COALESCE(SUM(-t.purchase_keys_delta_cents), 0) AS revenueCents
            FROM key_transactions t
            JOIN pet_catalog_items i ON i.id = t.pet_catalog_item_id
            JOIN catalog_integration_requests r ON r.result_catalog_item_id = i.id
            WHERE r.commercial_id = :commercialId
              AND t.type = 'DEBIT_PET_GAME'
              AND t.created_at >= :from
              AND t.created_at < :to
            GROUP BY DATE(t.created_at)
            ORDER BY day
            """, nativeQuery = true)
    List<PetDailySalesRow> findPetDailySalesByCommercial(
            @Param("commercialId") Long commercialId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /** Proyección de {@link #findPetDailySalesByCommercial}. */
    interface PetDailySalesRow {
        java.sql.Date getDay();
        long getUnitsSold();
        long getRevenueCents();
    }

    /** Compradores que adquirieron el mismo producto más de una vez. */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT w.consumer_id
                FROM key_transactions t
                JOIN key_wallets w ON w.id = t.key_wallet_id
                WHERE t.pet_catalog_item_id = :catalogItemId AND t.type = 'DEBIT_PET_GAME'
                GROUP BY w.consumer_id HAVING COUNT(*) > 1
            ) AS repetidores
            """, nativeQuery = true)
    long countRepeatBuyers(@Param("catalogItemId") Long catalogItemId);

    /** Proyección de {@link #findPetProductSalesByCommercial}. */
    interface PetProductSalesRow {
        Long getCatalogItemId();
        Integer getExternalId();
        String getProductName();
        Integer getPriceKeys();
        Boolean getActive();
        long getUnitsSold();
        long getRevenueCents();
        long getUniqueBuyers();
        java.sql.Timestamp getFirstSale();
        java.sql.Timestamp getLastSale();
    }

    /** Marca en bulk como procesadas todas las transacciones del lote. */
    @Modifying
    @Query("UPDATE KeyTransaction kt SET kt.expiryProcessed = true WHERE kt.id IN :ids")
    void markAllAsProcessed(@Param("ids") List<UUID> ids);
}
