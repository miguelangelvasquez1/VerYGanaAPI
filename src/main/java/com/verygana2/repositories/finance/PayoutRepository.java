package com.verygana2.repositories.finance;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Payout;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    @Query("""
            SELECT SUM(p.netAmountCents)
            FROM Payout p
            WHERE p.commercial.id = :commercialId
            AND p.paidAt >= :startDate
            AND p.paidAt < :endDate
            """)
    BigDecimal sumTotalByCommercialIdAndPeriod(@Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /** Todos los payouts creados en el rango (para el endpoint de monitoreo del admin). */
    List<Payout> findByScheduledAtBetweenOrderByScheduledAtDesc(
            ZonedDateTime start, ZonedDateTime end);

    /** Todos los payouts en un estado dado, sin restricción de fecha. Usado por processScheduledPayouts(). */
    List<Payout> findByStatus(PayoutStatus status);

    /** Payouts FAILED del ciclo anterior para reintento. */
    @Query("""
            SELECT p FROM Payout p
            WHERE p.status = :status
            AND p.scheduledAt >= :start
            AND p.scheduledAt < :end
            """)
    List<Payout> findFailedForRetry(
            @Param("status") PayoutStatus status,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    /** Busca el Payout vinculado a una WompiTransaction — usado por el webhook handler. */
    Optional<Payout> findByWompiTransactionId(UUID wompiTransactionId);

    /**
     * Agregado por estado para los gauges de {@link com.verygana2.config.metrics.PayoutMetrics}:
     * cuántos payouts siguen pendientes, cuánta plata representan y desde cuándo espera el más
     * viejo. Una sola consulta en vez de tres por estado, porque corre cada minuto.
     */
    @Query("""
            SELECT p.status AS status,
                   COUNT(p) AS total,
                   COALESCE(SUM(p.netAmountCents), 0) AS netCents,
                   MIN(p.scheduledAt) AS oldestScheduledAt
            FROM Payout p
            WHERE p.status IN :statuses
            GROUP BY p.status
            """)
    List<PayoutStatusAggregate> aggregateByStatus(@Param("statuses") Collection<PayoutStatus> statuses);

    /** Proyección de {@link #aggregateByStatus(Collection)}. */
    interface PayoutStatusAggregate {
        PayoutStatus getStatus();

        long getTotal();

        long getNetCents();

        /** Null solo si el estado no tiene payouts, caso que el GROUP BY ya excluye. */
        ZonedDateTime getOldestScheduledAt();
    }

    /** Historial paginado de payouts de un comercial filtrado por período — panel de facturación. */
    @Query("""
            SELECT p FROM Payout p
            WHERE p.commercial.id = :commercialId
            AND p.scheduledAt >= :from
            AND p.scheduledAt < :to
            ORDER BY p.scheduledAt DESC
            """)
    Page<Payout> findByCommercialIdAndPeriod(
            @Param("commercialId") Long commercialId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to,
            Pageable pageable);
}
