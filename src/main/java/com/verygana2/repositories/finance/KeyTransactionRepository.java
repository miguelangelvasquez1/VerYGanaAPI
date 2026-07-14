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

    /** Marca en bulk como procesadas todas las transacciones del lote. */
    @Modifying
    @Query("UPDATE KeyTransaction kt SET kt.expiryProcessed = true WHERE kt.id IN :ids")
    void markAllAsProcessed(@Param("ids") List<UUID> ids);
}
