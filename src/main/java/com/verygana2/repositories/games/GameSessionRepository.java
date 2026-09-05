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

    /** Partidas completadas de las campañas de un comercial en un rango (panel de inicio). */
    @Query("""
        SELECT COUNT(gs) FROM GameSession gs
        WHERE gs.campaign.commercial.id = :commercialId
        AND gs.completed = true
        AND gs.startTime >= :start AND gs.startTime < :end
    """)
    long countCompletedByCommercialAndDateRange(
            @Param("commercialId") Long commercialId,
            @Param("start") java.time.ZonedDateTime start,
            @Param("end") java.time.ZonedDateTime end);

    // ── Reportes de rendimiento del comercial ────────────────────────────────

    /**
     * Una fila: [Long sessionsPlayed, Long completedSessions, Long uniquePlayers,
     * Long totalPlayTimeSeconds, Long coinsEarnedCents] de las campañas del comercial en el rango.
     */
    @Query("""
        SELECT COUNT(gs),
               COALESCE(SUM(CASE WHEN gs.completed = true THEN 1 ELSE 0 END), 0),
               COUNT(DISTINCT gs.consumer.id),
               COALESCE(SUM(gs.playTimeSeconds), 0),
               COALESCE(SUM(gs.coinsEarned), 0)
        FROM GameSession gs
        WHERE gs.campaign.commercial.id = :commercialId
          AND gs.startTime >= :from AND gs.startTime < :to
    """)
    List<Object[]> aggregateByCommercialInRange(
            @Param("commercialId") Long commercialId,
            @Param("from") java.time.ZonedDateTime from,
            @Param("to") java.time.ZonedDateTime to);

    /**
     * [Long campaignId, Long sessionsPlayed, Long completedSessions, Long uniquePlayers,
     * Long totalPlayTimeSeconds] por campaña del comercial en el rango.
     */
    @Query("""
        SELECT gs.campaign.id,
               COUNT(gs),
               COALESCE(SUM(CASE WHEN gs.completed = true THEN 1 ELSE 0 END), 0),
               COUNT(DISTINCT gs.consumer.id),
               COALESCE(SUM(gs.playTimeSeconds), 0)
        FROM GameSession gs
        WHERE gs.campaign.commercial.id = :commercialId
          AND gs.startTime >= :from AND gs.startTime < :to
        GROUP BY gs.campaign.id
    """)
    List<Object[]> aggregateByCampaignInRange(
            @Param("commercialId") Long commercialId,
            @Param("from") java.time.ZonedDateTime from,
            @Param("to") java.time.ZonedDateTime to);

    /** [java.sql.Date day, Long count] de partidas iniciadas del comercial por día del rango. */
    @Query("""
        SELECT DATE(gs.startTime), COUNT(gs) FROM GameSession gs
        WHERE gs.campaign.commercial.id = :commercialId
          AND gs.startTime >= :from AND gs.startTime < :to
        GROUP BY DATE(gs.startTime)
    """)
    List<Object[]> countByDayInRange(
            @Param("commercialId") Long commercialId,
            @Param("from") java.time.ZonedDateTime from,
            @Param("to") java.time.ZonedDateTime to);
}
