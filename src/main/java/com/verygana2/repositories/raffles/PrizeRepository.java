package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.verygana2.models.raffles.Prize;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Long> {
    
    /**
     * Encuentra todos los premios de una rifa
     */
    List<Prize> findByRaffleIdOrderByPositionAsc(Long raffleId);

    /**
     * Premios PENDING cuyos ganadores no reclamados ya superaron su claim_deadline
     * y no queda ningún ganador con plazo vigente (cubre quantity > 1).
     */
    @Query("""
            SELECT p FROM Prize p
            WHERE p.prizeStatus = com.verygana2.models.enums.raffles.PrizeStatus.PENDING
            AND EXISTS (SELECT 1 FROM RaffleWinner w WHERE w.prize = p)
            AND NOT EXISTS (
                SELECT 1 FROM RaffleWinner w
                WHERE w.prize = p
                AND w.prizeClaimed = false
                AND w.claimDeadline > :now
            )
            """)
    List<Prize> findOverdueUnclaimedPrizes(@Param("now") ZonedDateTime now);
}