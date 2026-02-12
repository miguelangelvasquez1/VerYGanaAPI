package com.verygana2.repositories.raffles;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;

public interface RaffleRuleRespository extends JpaRepository<RaffleRule, Long>{

    @Query("""
            SELECT r FROM RaffleRule r 
            WHERE r.raffle.id = :raffleId
            AND r.ticketEarningRule.ruleType = :type
            """)
    Optional<RaffleRule> findByRaffleIdAndRuleType(@Param("raffleId") Long raffleId, @Param("type") TicketEarningRuleType type);
}
