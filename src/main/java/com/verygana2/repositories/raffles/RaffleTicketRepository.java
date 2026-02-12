package com.verygana2.repositories.raffles;

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

import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.RaffleTicket;

@Repository
public interface RaffleTicketRepository extends JpaRepository<RaffleTicket, Long> {

        // ========== BÚSQUEDAS BÁSICAS ==========

        /**
         * Encuentra un ticket por su número único
         */
        Optional<RaffleTicket> findByTicketNumber(String ticketNumber);

        /**
         * Verifica si existe un ticket con ese número
         */
        boolean existsByTicketNumber(String ticketNumber);

        // ========== CONTADORES POR USUARIO ==========

        /**
         * Cuenta tickets de un usuario en una rifa específica
         */
        long countByTicketOwnerIdAndRaffleId(Long ticketOwnerId, Long raffleId);

        /**
         * Cuenta tickets de un usuario en una rifa segun el estado del ticket
         */
        long countByTicketOwnerIdAndRaffleIdAndStatus(
                        Long ticketOwnerId,
                        Long raffleId,
                        RaffleTicketStatus status);

        /**
         * Cuenta tickets totales de un usuario (todas las rifas)
         */
        long countByTicketOwnerIdAndStatus(Long ticketOwnerId, RaffleTicketStatus status);

        /**
         * Cuenta tickets por fuente y rifa (para verificar límites)
         */
        long countByTicketOwnerIdAndRaffleIdAndSource(
                        Long ticketOwnerId,
                        Long raffleId,
                        RaffleTicketSource source);

        @Query("""
                        SELECT t.raffle.id, t.raffle.title, t.raffle.raffleType,
                               COUNT(t), t.raffle.drawDate, t.raffle.raffleStatus
                        FROM RaffleTicket t
                        WHERE t.ticketOwner.id = :ticketOwnerId
                        AND t.status = 'ACTIVE'
                        GROUP BY t.raffle.id, t.raffle.title, t.raffle.raffleType,
                                 t.raffle.drawDate, t.raffle.raffleStatus
                        ORDER BY t.raffle.drawDate DESC
                        """)
        List<Object[]> countTicketsByTicketOwnerGroupedByRaffle(@Param("ticketOwnerId") Long ticketOwnerId);
        // ========== BÚSQUEDAS CON FILTROS ==========

        /**
         * Tickets de un usuario en una rifa con paginación
         */
        Page<RaffleTicket> findByTicketOwnerIdAndRaffleId(
                        Long ticketOwnerId,
                        Long raffleId,
                        Pageable pageable);

        /**
         * Tickets de un usuario con filtros
         */
        @Query("SELECT t FROM RaffleTicket t WHERE t.ticketOwner.id = :ticketOwnerId " +
                        "AND (:status IS NULL OR t.status = :status) " +
                        "AND (:source IS NULL OR t.source = :source) " +
                        "AND (:issuedAt IS NULL OR t.issuedAt >= :issuedAt) " +
                        "ORDER BY t.issuedAt DESC")
        Page<RaffleTicket> findUserTicketsWithFilters(
                        @Param("ticketOwnerId") Long ticketOwnerId,
                        @Param("status") RaffleTicketStatus status,
                        @Param("source") RaffleTicketSource source,
                        @Param("issuedAt") ZonedDateTime issuedAt,
                        Pageable pageable);

        /**
         * Tickets de una rifa con filtros (para admin)
         */
        @Query("SELECT t FROM RaffleTicket t WHERE t.raffle.id = :raffleId " +
                        "AND (:status IS NULL OR t.status = :status) " +
                        "AND (:source IS NULL OR t.source = :source) " +
                        "AND (:issuedAt IS NULL OR t.issuedAt >= :issuedAt) " +
                        "ORDER BY t.issuedAt DESC")
        Page<RaffleTicket> findRaffleTicketsWithFilters(
                        @Param("raffleId") Long raffleId,
                        @Param("status") RaffleTicketStatus status,
                        @Param("source") RaffleTicketSource source,
                        @Param("issuedAt") ZonedDateTime issuedAt,
                        Pageable pageable);

        // ========== TICKETS PARA SORTEO ==========

        /**
         * Obtiene todos los tickets activos de una rifa (para sorteo)
         * CRÍTICO: Solo tickets ACTIVE participan en el sorteo
         */
        List<RaffleTicket> findByRaffleIdAndStatus(Long raffleId, RaffleTicketStatus status);

        /**
         * Tickets ganadores de una rifa
         */
        List<RaffleTicket> findByRaffleIdAndIsWinnerTrue(Long raffleId);

        // ========== OPERACIONES MASIVAS ==========

        /**
         * Expira todos los tickets activos de una rifa
         */
        @Modifying
        @Query("UPDATE RaffleTicket t SET t.status = 'EXPIRED', t.usedAt = :now " +
                        "WHERE t.raffle.id = :raffleId AND t.status = 'ACTIVE'")
        int expireTicketsByRaffle(
                        @Param("raffleId") Long raffleId,
                        @Param("now") ZonedDateTime now);

        /**
         * Marca un ticket como ganador
         */
        @Modifying
        @Query("UPDATE RaffleTicket t SET t.isWinner = true " +
                        "WHERE t.id = :ticketId")
        int markAsWinner(@Param("ticketId") Long ticketId);

        // ========== ESTADÍSTICAS ==========

        /**
         * Cuenta tickets por fuente en una rifa
         */
        @Query("SELECT t.source, COUNT(t) FROM RaffleTicket t " +
                        "WHERE t.raffle.id = :raffleId GROUP BY t.source")
        List<Object[]> countTicketsBySource(@Param("raffleId") Long raffleId);

        /**
         * Cuenta tickets de un usuario por fuente en un rango de fechas
         */
        long countByTicketOwnerIdAndSourceAndIssuedAtBetween(
                        Long ticketOwnerId,
                        RaffleTicketSource source,
                        ZonedDateTime start,
                        ZonedDateTime end);

        /**
         * Cuenta tickets totales de un usuario por fuente
         */
        long countByTicketOwnerIdAndSource(
                        Long ticketOwnerId,
                        RaffleTicketSource source);
}