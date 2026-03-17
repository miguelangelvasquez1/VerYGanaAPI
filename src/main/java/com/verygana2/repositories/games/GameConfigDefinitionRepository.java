package com.verygana2.repositories.games;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.GameConfigDefinition;

@Repository
public interface GameConfigDefinitionRepository extends JpaRepository<GameConfigDefinition, Long> {
    
    Optional<GameConfigDefinition> findFirstByGameIdOrderByVersionDesc(Long gameId);

    List<GameConfigDefinition> findByGameId(Long gameId);

    Optional<GameConfigDefinition> findTopByGameIdOrderByVersionDesc(Long gameId);
}
