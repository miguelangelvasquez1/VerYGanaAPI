package com.verygana2.repositories.games;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.Campaign;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    List<Campaign> findByAdvertiserId(Long advertiserId);

    // @Query("""
    //     select new com.verygana2.dtos.game.campaign.CampaignDTO(
    //         c.id, g.id, g.title, c.budget, c.spent, c.sessionsPlayed, c.completedSessions,
    //         c.totalPlayTimeSeconds, c.status, c.startDate, c.endDate, c.createdAt, c.updatedAt
    //     )
    //     from Campaign c
    //     join c.game g
    //     where c.advertiser.id = :advertiserId
    //     order by c.createdAt desc
    // """)
    // List<CampaignDTO> findCampaignsForAdvertiser(@Param("advertiserId") Long advertiserId);


    Optional<Campaign> findRandomActiveCampaignByGameId(Long gameId);

    boolean existsByAdvertiserIdAndGameId(Long advertiserId, Long gameId);
}
