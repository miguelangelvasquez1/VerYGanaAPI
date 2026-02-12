package com.verygana2.repositories.games;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.GameConfigDefinition;

@Repository
public interface GameConfigDefinitionRepository extends JpaRepository<GameConfigDefinition, Long> {
    
    List<GameConfigDefinition> findByGameId(Long gameId);
}
