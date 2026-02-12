package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, Long> {

        // ========== BÚSQUEDAS BÁSICAS ==========

        /**
         * Encuentra rifas por tipo
         */
        Page<Raffle> findByRaffleType(RaffleType type, Pageable pageable);

        /**
         * Encuentra rifas activas por tipo
         */
        @Query("""
                        SELECT r FROM Raffle r
                        WHERE (:status IS NULL OR r.raffleStatus = :status)
                        AND (:type IS NULL OR r.raffleType = :type)
                        """)
        Page<Raffle> findByRaffleStatusAndRaffleType(
                        @Param("status") RaffleStatus status,
                        @Param("type") RaffleType type,
                        Pageable pageable);

        // ========== BÚSQUEDAS POR FECHAS ==========

        /**
         * Encuentra rifas que terminan antes de una fecha
         * Útil para cerrar rifas expiradas automáticamente
         */
        List<Raffle> findByEndDateBeforeAndRaffleStatus(
                        ZonedDateTime endDate,
                        RaffleStatus status);

        /**
         * Encuentra rifas cuyo sorteo es antes de una fecha
         * Útil para notificar sorteos próximos
         */
        List<Raffle> findByDrawDateBeforeAndRaffleStatus(
                        ZonedDateTime drawDate,
                        RaffleStatus status);

        /**
         * Rifas activas en un rango de fechas
         */
        @Query("SELECT r FROM Raffle r WHERE r.raffleStatus = :status " +
                        "AND r.startDate <= :now AND r.endDate >= :now")
        List<Raffle> findActiveRafflesNow(
                        @Param("status") RaffleStatus status,
                        @Param("now") ZonedDateTime now);

        // ========== BÚSQUEDAS CON PAGINACIÓN ==========

        /**
         * Lista rifas por estado
         */
        List<Raffle> findByRaffleStatus(RaffleStatus status);

        /**
         * Busca rifas por tipo y estado con paginación
         */
        Page<Raffle> findByRaffleTypeAndRaffleStatus(
                        RaffleType type,
                        RaffleStatus status,
                        Pageable pageable);

        // ========== CONSULTAS ESPECÍFICAS ==========

        /**
         * Verifica si existe una rifa activa de un tipo
         */
        boolean existsByRaffleTypeAndRaffleStatus(RaffleType type, RaffleStatus status);

        /**
         * Cuenta rifas activas
         */
        long countByRaffleStatus(RaffleStatus status);

        /**
         * Obtiene rifas creadas por un admin
         */
        @Query("""
                        SELECT r FROM Raffle r
                        WHERE r.createdBy = :adminId
                        ORDER BY r.createdAt DESC
                                    """)
        Page<Raffle> findByCreatedByAdminId(Long adminId, Pageable pageable);

        // ========== CONSULTAS COMPLEJAS ==========

        /**
         * Rifas próximas a sortear (en las próximas 24 horas)
         */
        @Query("SELECT r FROM Raffle r WHERE r.raffleStatus = 'ACTIVE' " +
                        "AND r.drawDate BETWEEN :now AND :tomorrow")
        List<Raffle> findUpcomingDraws(
                        @Param("now") ZonedDateTime now,
                        @Param("tomorrow") ZonedDateTime tomorrow);

        /**
         * Rifas con tickets disponibles (no llegaron al límite)
         */
        @Query("SELECT r FROM Raffle r WHERE r.raffleStatus = 'ACTIVE' " +
                        "AND (r.maxTotalTickets IS NULL OR r.totalTicketsIssued < r.maxTotalTickets)")
        List<Raffle> findRafflesWithAvailableTickets();

        @Query("""
                            SELECT r FROM Raffle r
                            LEFT JOIN FETCH r.raffleRules rl
                            LEFT JOIN FETCH rl.ticketEarningRule
                            WHERE r.raffleStatus = 'ACTIVE'
                            AND r.startDate <= :now
                            AND r.endDate > :now
                            ORDER BY r.drawDate ASC
                        """)
        List<Raffle> findActiveRaffleByDrawDate(@Param("now") ZonedDateTime now);

}