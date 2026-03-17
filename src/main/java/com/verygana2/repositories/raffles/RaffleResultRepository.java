package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.verygana2.models.raffles.RaffleResult;

public interface RaffleResultRepository extends JpaRepository<RaffleResult, Long>{
    @Query("""
            SELECT rf from RaffleResult rf
            ORDER BY rf.drawnAt DESC
            LIMIT 10
            """)
    List<RaffleResult> findLastRaffleResults();

    Optional<RaffleResult> findByRaffleId(Long raffleId);
}
