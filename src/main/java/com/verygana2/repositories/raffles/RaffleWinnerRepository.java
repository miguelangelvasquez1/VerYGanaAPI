package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
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
     * Encuentra todos los ganadores de un resultado de una rifa
     */
    List<RaffleWinner> findByRaffleResultId(Long raffleResultId);

    /**
     * Encuentra ganadores por estado de premio
     */
    List<RaffleWinner> findByRaffleResultIdAndPrizePrizeStatus(
            Long raffleResultId,
            PrizeStatus status);

    /**
     * Encuentra todos los premios ganados por un usuario
     */
    Page<RaffleWinner> findByWinnerId(Long consumerId, Pageable pageable);

    /**
     * Encuentra ganador por ticket
     */
    Optional<RaffleWinner> findByWinningTicketId(Long ticketId);

    /**
     * Cuenta ganadores de una rifa
     */
    long countByRaffleResultId(Long raffleResultId);

    /**
     * Ganadores que no han reclamado su premio
     */
    @Query("SELECT w FROM RaffleWinner w WHERE w.raffleResult.id = :raffleResultId " +
            "AND w.prizeClaimed = false")
    List<RaffleWinner> findUnclaimedWinners(@Param("raffleResultId") Long raffleResultId);

    /**
     * Cuenta premios reclamados de una rifa
     */
    long countByRaffleResultIdAndPrizeClaimedTrue(Long raffleResultId);

    @Query("""
            SELECT w FROM RaffleWinner w
            JOIN FETCH w.prize p
            JOIN FETCH w.winner c
            ORDER BY w.createdAt DESC
            LIMIT 20
                    """)
    List<RaffleWinner> findLastWinners();
}