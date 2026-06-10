package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdWatchSessionStatus;

@Repository
public interface AdWatchSessionRepository extends JpaRepository<AdWatchSession, UUID> {
    
    Optional<AdWatchSession> findByIdAndConsumerIdAndAdId(UUID id, Long consumerId, Long adId);

    Optional<AdWatchSession> findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
        Long consumerId,
        AdWatchSessionStatus status,
        ZonedDateTime now
    );

    @Query("""
        SELECT COUNT(s) FROM AdWatchSession s
        WHERE s.consumer.id = :consumerId
        AND s.ad.id = :adId
        AND s.status = :status
        AND s.startedAt >= :todayStart
    """)
    long countByConsumerAdAndStatusSince(
        @Param("consumerId") Long consumerId,
        @Param("adId") Long adId,
        @Param("status") AdWatchSessionStatus status,
        @Param("todayStart") ZonedDateTime todayStart
    );

    /**
     * Retorna la última vez que el consumidor visualizó cada uno de los anuncios indicados.
     * Cada elemento del resultado es un Object[] con: [adId (Long), maxStartedAt (ZonedDateTime)].
     */
    @Query("""
        SELECT s.ad.id, MAX(s.startedAt)
        FROM AdWatchSession s
        WHERE s.consumer.id = :consumerId
        AND s.ad.id IN :adIds
        GROUP BY s.ad.id
    """)
    List<Object[]> findLastViewedAtByAdIds(
            @Param("consumerId") Long consumerId,
            @Param("adIds") Collection<Long> adIds
    );
}
