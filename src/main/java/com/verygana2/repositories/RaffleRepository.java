package com.verygana2.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.raffles.Raffle;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, String> {
    // List<Raffle> findByState(RaffleState state);
    Optional<Raffle> findByName(String name);
    List<Raffle> findByDrawDateBefore(LocalDateTime dateTime);
}
