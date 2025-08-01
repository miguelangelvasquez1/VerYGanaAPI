package com.VerYGana.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.Raffle;
import com.VerYGana.models.Enums.RaffleState;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, String> {
    List<Raffle> findByState(RaffleState state);
    Optional<Raffle> findByName(String name);
    List<Raffle> findByEndDateBefore(LocalDateTime dateTime);
}
