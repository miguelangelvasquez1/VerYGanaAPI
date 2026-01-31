package com.verygana2.repositories.games;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.GameConfig;

@Repository
public interface GameConfigRepository extends JpaRepository<GameConfig, Long> {
    List<GameConfig> findByCampaignId(Long campaignId);
}