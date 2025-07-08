package com.Rifacel.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Raffle;
import com.Rifacel.models.Enums.RaffleState;

public interface RaffleRepository extends JpaRepository<Raffle, String> {
    List<Raffle> findByState(RaffleState state);
    Optional<Raffle> findByName(String name);
    List<Raffle> findByEndDateBefore(LocalDateTime dateTime);
}
