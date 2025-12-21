package com.verygana2.repositories.games;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.GameSessionMetric;

@Repository
public interface GameSessionMetricRepository extends JpaRepository<GameSessionMetric, Long> {

}
