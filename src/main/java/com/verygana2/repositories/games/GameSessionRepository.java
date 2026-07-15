package com.verygana2.repositories.games;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.games.GameSession;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findBySessionToken(String sessionToken);

    /**
     * Retorna la última vez que el consumidor jugó cada una de las campañas indicadas.
     * Cada elemento del resultado es un Object[] con: [campaignId (Long), maxStartTime (ZonedDateTime)].
     */
    @Query("""
        SELECT gs.campaign.id, MAX(gs.startTime)
        FROM GameSession gs
        WHERE gs.consumer.id = :consumerId
        AND gs.campaign.id IN :campaignIds
        GROUP BY gs.campaign.id
    """)
    List<Object[]> findLastPlayedAtByCampaignIds(
            @Param("consumerId") Long consumerId,
            @Param("campaignIds") Collection<Long> campaignIds
    );
}
