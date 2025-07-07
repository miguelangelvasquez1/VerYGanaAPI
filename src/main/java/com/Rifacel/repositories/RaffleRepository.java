package com.Rifacel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Raffle;

public interface RaffleRepository extends JpaRepository<Raffle, String> {
    // Additional query methods can be defined here if needed
    
}
