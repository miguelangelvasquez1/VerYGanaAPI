package com.verygana2.repositories.games;

import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.games.GameMetricDefinition;

@Repository
public interface GameMetricDefinitionRepository extends JpaRepository<GameMetricDefinition, Long> {

    List<GameMetricDefinition> findByGameId(Long gameId);
}
