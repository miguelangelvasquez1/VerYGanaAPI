package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.models.raffles.RaffleParticipation;

@Repository
public interface RaffleParticipationRepository extends JpaRepository<RaffleParticipation, Long> {
    
    /**
     * Encuentra participación de un usuario en una rifa
     */
    Optional<RaffleParticipation> findByConsumerIdAndRaffleId(Long consumerId, Long raffleId);
    
    /**
     * Verifica si un usuario participa en una rifa
     */
    boolean existsByConsumerIdAndRaffleId(Long consumerId, Long raffleId);
    
    /**
     * Participaciones de un usuario con paginación
     */
    Page<RaffleParticipation> findByConsumerId(Long consumerId, Pageable pageable);
    
    /**
     * Todas las participaciones de una rifa
     */
    Page<RaffleParticipation> findByRaffleId(Long raffleId, Pageable pageable);
    
    /**
     * Cuenta participantes de una rifa
     */
    long countByRaffleId(Long raffleId);
    
    /**
     * Encuentra el leaderboard de participantes de una rifa
     * Ordena por cantidad de tickets descendente
     */
    @Query("""
        SELECT DISTINCT new com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO(
            p.consumer.id,
            p.consumer.userName,
            p.consumer.profileImageUrl,
            p.ticketsCount,
            (CAST(p.ticketsCount AS double) * 100.0 / CAST(p.raffle.totalTicketsIssued AS double))
        )
        FROM RaffleParticipation p
        WHERE p.raffle.id = :raffleId
        AND p.ticketsCount > 0
        ORDER BY p.ticketsCount DESC
        """)
    List<ParticipantLeaderboardDTO> findLeaderboard(
        @Param("raffleId") Long raffleId,
        Pageable pageable
    );
}