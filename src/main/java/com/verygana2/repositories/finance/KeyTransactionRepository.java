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

    @Query("SELECT kt FROM KeyTransaction kt WHERE kt.keyWallet.consumer.id = :consumerId ORDER BY kt.createdAt DESC")
    Page<KeyTransaction> findByConsumerId(@Param("consumerId") Long consumerId, Pageable pageable);

    @Query("SELECT kt FROM KeyTransaction kt WHERE kt.keyWallet.consumer.id = :consumerId AND kt.type = :type ORDER BY kt.createdAt DESC")
    Page<KeyTransaction> findByConsumerIdAndType(@Param("consumerId") Long consumerId, @Param("type") KeyTransactionType type, Pageable pageable);

    /**
     * Devuelve todos los créditos de llaves que ya vencieron y aún no se procesaron.
     * Solo créditos (delta > 0) para evitar contar débitos y reservas.
     */
    @Query("""
            SELECT kt FROM KeyTransaction kt
            JOIN FETCH kt.keyWallet kw
            WHERE kt.expiresAt IS NOT NULL
            AND kt.expiresAt < :now
            AND kt.expiryProcessed = false
            AND (kt.purchaseKeysDelta > 0 OR kt.connectivityKeysDelta > 0)
            ORDER BY kt.keyWallet.id
            """)
    List<KeyTransaction> findExpiredNotProcessed(@Param("now") ZonedDateTime now);

    /** Marca en bulk como procesadas todas las transacciones del lote. */
    @Modifying
    @Query("UPDATE KeyTransaction kt SET kt.expiryProcessed = true WHERE kt.id IN :ids")
    void markAllAsProcessed(@Param("ids") List<UUID> ids);
}
