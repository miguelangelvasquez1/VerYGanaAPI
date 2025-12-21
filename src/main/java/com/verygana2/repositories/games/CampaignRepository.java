package com.verygana2.repositories.games;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.Campaign;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    Optional<Campaign> findRandomActiveCampaignByGameId(Long gameId);
}
