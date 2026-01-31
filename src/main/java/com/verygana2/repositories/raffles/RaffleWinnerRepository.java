package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.PrizeStatus;
import com.verygana2.models.raffles.RaffleWinner;

@Repository
public interface RaffleWinnerRepository extends JpaRepository<RaffleWinner, Long> {
    
    /**
     * Encuentra todos los ganadores de una rifa
     */
    List<RaffleWinner> findByRaffleId(Long raffleId);
    
    /**
     * Encuentra ganadores por estado de premio
     */
    List<RaffleWinner> findByRaffleIdAndPrizePrizeStatus(
        Long raffleId, 
        PrizeStatus status
    );
    
    /**
     * Encuentra todos los premios ganados por un usuario
     */
    List<RaffleWinner> findByWinnerId(Long consumerId, Pageable pageable);
    
    /**
     * Verifica si un usuario ganó en una rifa específica
     */
    boolean existsByWinnerIdAndRaffleId(Long consumerId, Long raffleId);
    
    /**
     * Encuentra ganador por ticket
     */
    Optional<RaffleWinner> findByWinningTicketId(Long ticketId);
    
    /**
     * Cuenta ganadores de una rifa
     */
    long countByRaffleId(Long raffleId);
    
    /**
     * Ganadores que no han reclamado su premio
     */
    @Query("SELECT w FROM RaffleWinner w WHERE w.raffle.id = :raffleId " +
           "AND w.prizeClaimed = false")
    List<RaffleWinner> findUnclaimedWinners(@Param("raffleId") Long raffleId);
    
    /**
     * Cuenta premios reclamados de una rifa
     */
    long countByRaffleIdAndPrizeClaimedTrue(Long raffleId);
}