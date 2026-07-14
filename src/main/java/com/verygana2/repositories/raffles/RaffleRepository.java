package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
         * Encuentra rifas por filtros
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
                        AND (:search IS NULL OR :search = ''
                        OR LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:type IS NULL OR r.raffleType = :type)
                        AND (:drawDateStart IS NULL OR r.drawDate >= :drawDateStart)
                        AND (:drawDateEnd IS NULL OR r.drawDate < :drawDateEnd)
                        GROUP BY r.id, r.title, r.imageAsset.objectKey, r.raffleType, r.raffleStatus,
                             r.startDate, r.endDate, r.drawDate, r.totalTicketsIssued,
                             r.totalParticipants, r.requiresPet
                        """)
        Page<RaffleSummaryResponseDTO> findByFilters(
                        @Param("status") RaffleStatus status,
                        @Param("search") String search,
                        @Param("drawDateStart") ZonedDateTime drawDateStart,
                        @Param("drawDateEnd") ZonedDateTime drawDateEnd,
                        @Param("type") RaffleType type,
                        Pageable pageable);

        /**
         * Cuenta rifas activas
         */
        long countByRaffleStatus(RaffleStatus status);

        // ========== CONSULTAS COMPLEJAS ==========

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
                            AND r.drawDate > :now
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

        // LIVE: drawDate está en el futuro pero dentro de la próxima hora
        @Query("""
                            SELECT r FROM Raffle r
                            WHERE r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.CLOSED
                            AND r.drawDate > :now
                            AND r.drawDate <= :liveThreshold
                            ORDER BY r.drawDate ASC
                        """)
        List<Raffle> findRafflesToSetLive(
                        @Param("now") ZonedDateTime now,
                        @Param("liveThreshold") ZonedDateTime liveThreshold);

        // Rifas CLOSED cuya drawDate ya pasó sin haberse sorteado (servidor caído,
        // etc.)
        @Query("""
                            SELECT r FROM Raffle r
                            WHERE r.raffleStatus = com.verygana2.models.enums.raffles.RaffleStatus.CLOSED
                            AND r.drawDate <= :now
                            ORDER BY r.drawDate ASC
                        """)
        List<Raffle> findMissedDrawRaffles(@Param("now") ZonedDateTime now);

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

        /**
         * Trae la rifa junto con sus premios e imágenes ya inicializados en una sola
         * consulta, evitando LazyInitializationException al usar la entidad fuera
         * de un contexto transaccional (ej. desde un scheduler).
         */
        @Query("""
                        SELECT DISTINCT r FROM Raffle r
                        LEFT JOIN FETCH r.prizes p
                        LEFT JOIN FETCH p.imageAsset
                        WHERE r.id = :raffleId
                        """)
        Optional<Raffle> findByIdWithPrizes(@Param("raffleId") Long raffleId);

}