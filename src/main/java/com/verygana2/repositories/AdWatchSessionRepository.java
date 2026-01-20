package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdWatchSessionStatus;

@Repository
public interface AdWatchSessionRepository extends JpaRepository<AdWatchSession, UUID> {
    
    Optional<AdWatchSession> findByIdAndUserIdAndAdId(UUID id, Long userId, Long adId);

    Optional<AdWatchSession> findByUserIdAndStatusAndExpiresAtAfter(
        Long userId,
        AdWatchSessionStatus status,
        ZonedDateTime now
    );

    @Modifying
    @Query("""
        UPDATE AdWatchSession s
        SET s.status = EXPIRED
        WHERE s.status = ACTIVE
        AND s.expiresAt <= :now
    """)
    int expireActiveSessions(@Param("now") ZonedDateTime now);
}
