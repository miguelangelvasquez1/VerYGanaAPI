package com.verygana2.repositories.raffles;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.raffles.RaffleParticipation;

@Repository
public interface RaffleParticipationRepository extends JpaRepository<RaffleParticipation, Long> {
    
    /**
     * Encuentra participación de un usuario en una rifa
     */
    Optional<RaffleParticipation> findByConsumerIdAndRaffleId(Long consumerId, Long raffleId);
    
}