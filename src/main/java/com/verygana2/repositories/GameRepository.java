package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.Game;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    
    Optional<Game> findByIdAndActiveTrue(Long id);
}
