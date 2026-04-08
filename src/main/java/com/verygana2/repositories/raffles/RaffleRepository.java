package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, Long> {

    // ========== CONSULTAS PARA ADMIN ==========
    /**
     * Encuentra rifas por tipo y estado para admin view
     */
    @Query("""
            SELECT new com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO(
            r.id,
            r.title,
            r.imageAsset.objectKey,
            r.raffleType,
            r.raffleStatus,
            r.startDate,
            r.endDate,
            r.drawDate,
            r.totalTicketsIssued,
            r.totalParticipants,
            COUNT(p),
            r.requiresPet
            ) FROM Raffle r
            JOIN r.prizes p
            WHERE (:status IS NULL OR r.raffleStatus = :status)
            AND (:type IS NULL OR r.raffleType = :type)
            GROUP BY r.id, r.title, r.imageAsset.objectKey, r.raffleType, r.raffleStatus,
                 r.startDate, r.endDate, r.drawDate, r.totalTicketsIssued,
                 r.totalParticipants, r.requiresPet
            """)
    Page<RaffleSummaryResponseDTO> findByRaffleStatusAndRaffleType(
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

    @Query("""
                SELECT r FROM Raffle r
                WHERE r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.DRAFT
                AND r.startDate <= :now
                AND SIZE(r.prizes) > 0
                AND SIZE(r.raffleRules) > 0
                ORDER BY r.startDate ASC
            """)
    List<Raffle> findRafflesToActivate(@Param("now") ZonedDateTime now);

    @Query("""
                SELECT r FROM Raffle r
                WHERE r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.ACTIVE
                AND r.endDate <= :now
                ORDER BY r.endDate ASC
            """)
    List<Raffle> findRafflesToClose(@Param("now") ZonedDateTime now);

    @Query("""
                SELECT r FROM Raffle r
                WHERE r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.CLOSED
                AND r.drawDate >= :windowStart
                AND r.drawDate <= :liveThreshold
                ORDER BY r.drawDate ASC
            """)
    List<Raffle> findRafflesToSetLive(
            @Param("windowStart") ZonedDateTime windowStart,
            @Param("liveThreshold") ZonedDateTime liveThreshold);

    @Query("""
            SELECT r FROM Raffle r
            WHERE r.raffleStatus = 'LIVE'
            AND r.drawDate <= :horizon
            """)
    List<Raffle> findLiveRafflesWithDrawDateBefore(@Param("horizon") ZonedDateTime horizon);

    // ========== CONSULTAS PARA USUARIOS ==========
    @Query("""
            SELECT new com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO(
                   r.id,
                   r.title,
                   r.imageAsset.objectKey,
                   r.raffleType,
                   r.raffleStatus,
                   r.startDate,
                   r.endDate,
                   r.drawDate,
                   r.totalTicketsIssued,
                   r.totalParticipants,
                   COUNT(p),
                   r.requiresPet
                   ) FROM Raffle r
                   JOIN r.prizes p
                   WHERE (r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.LIVE)
                   GROUP BY r.id, r.title, r.imageAsset.objectKey, r.raffleType, r.raffleStatus,
                        r.startDate, r.endDate, r.drawDate, r.totalTicketsIssued,
                        r.totalParticipants, r.requiresPet
                    ORDER BY r.drawDate ASC
                    LIMIT 10
                """)
    List<RaffleSummaryResponseDTO> findLiveRaffles();

    @Query("""
            SELECT new com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO(
            r.id,
            r.title,
            r.imageAsset.objectKey,
            r.raffleType,
            r.raffleStatus,
            r.startDate,
            r.endDate,
            r.drawDate,
            r.totalTicketsIssued,
            r.totalParticipants,
            COUNT(p),
            r.requiresPet
            ) FROM Raffle r
            JOIN r.prizes p
            WHERE (r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.ACTIVE)
            AND (:type IS NULL OR r.raffleType = :type)
            GROUP BY r.id, r.title, r.imageAsset.objectKey, r.raffleType, r.raffleStatus,
                 r.startDate, r.endDate, r.drawDate, r.totalTicketsIssued,
                 r.totalParticipants, r.requiresPet
             ORDER BY r.drawDate ASC
            """)
    Page<RaffleSummaryResponseDTO> findActiveRaffles(
            @Param("type") RaffleType type,
            Pageable pageable);

    @Query("""
                SELECT new com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO(
                r.id,
                r.title,
                r.imageAsset.objectKey,
                r.raffleType,
                r.raffleStatus,
                r.drawDate,
                COUNT(DISTINCT t.id),
                CASE
                    WHEN MAX(CASE WHEN t.isWinner = true THEN 1 ELSE 0 END) = 1
                    THEN true
                    ELSE false
                END
                )
                FROM Raffle r
                JOIN r.issuedTickets t
                WHERE t.ticketOwner.id = :consumerId
                AND r.raffleStatus = :status
                GROUP BY r.id, r.title, r.imageAsset.objectKey, r.raffleType, r.raffleStatus, r.drawDate
                ORDER BY r.drawDate DESC
            """)
    Page<UserRaffleSummaryResponseDTO> findMyRafflesByStatus(@Param("consumerId") Long consumerId,
            @Param("status") RaffleStatus status, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT r.id)
            FROM Raffle r
            JOIN r.issuedTickets t
            WHERE t.ticketOwner.id = :consumerId
            AND r.raffleStatus = :status
                        """)
    long countMyRafflesByStatus(@Param("consumerId") Long consumerId, @Param("status") RaffleStatus status);

}