package com.verygana2.repositories.surveys;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.records.IssuanceTotals;
import com.verygana2.models.surveys.SurveyReward;

@Repository
public interface SurveyRewardRepository extends JpaRepository<SurveyReward, Long> {

    Page<SurveyReward> findBySessionConsumerId(Long consumerId, Pageable pageable);

    /** Filas anteriores a credited_amount. Usado solo por el backfill de arranque. */
    @Query("SELECT r FROM SurveyReward r WHERE r.creditedAmountCents IS NULL AND r.status = 'PROCESSED'")
    java.util.List<SurveyReward> findWithoutCreditedAmount();

    /**
     * Totales de emisión aún sin liquidar en tesorería, hasta un corte.
     * Solo recompensas PROCESSED: las PENDING todavía están dentro de su
     * transacción y su crédito puede revertirse.
     */
    @Query("""
            SELECT new com.verygana2.models.records.IssuanceTotals(
                       COALESCE(SUM(r.amountCents), 0L),
                       COALESCE(SUM(r.creditedAmountCents), 0L))
            FROM SurveyReward r
            WHERE r.issuanceSettled = false
              AND r.creditedAmountCents IS NOT NULL
              AND r.status = 'PROCESSED'
              AND r.grantedAt <= :cutoff
            """)
    IssuanceTotals sumUnsettledIssuance(@Param("cutoff") ZonedDateTime cutoff);

    /** Fecha de la recompensa más vieja sin liquidar. null si no hay ninguna. */
    @Query("""
            SELECT MIN(r.grantedAt) FROM SurveyReward r
            WHERE r.issuanceSettled = false AND r.creditedAmountCents IS NOT NULL
              AND r.status = 'PROCESSED'
            """)
    ZonedDateTime findOldestUnsettledAt();

    @Modifying
    @Query("""
            UPDATE SurveyReward r SET r.issuanceSettled = true
            WHERE r.issuanceSettled = false
              AND r.creditedAmountCents IS NOT NULL
              AND r.status = 'PROCESSED'
              AND r.grantedAt <= :cutoff
            """)
    int markIssuanceSettled(@Param("cutoff") ZonedDateTime cutoff);

    // Lo ACREDITADO, no lo financiado. El fallback a amountCents cubre las filas
    // anteriores a credited_amount, donde el dato solo existe en KeyTransaction.
    @Query("""
            SELECT COALESCE(SUM(COALESCE(r.creditedAmountCents, r.amountCents)), 0)
            FROM SurveyReward r
            WHERE r.session.consumer.id = :consumerId AND r.status = 'PROCESSED'
            """)
    BigDecimal getTotalRewardsByConsumer(@Param("consumerId") Long consumerId);
}
