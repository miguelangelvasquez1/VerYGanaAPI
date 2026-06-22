package com.verygana2.repositories.levels;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.levels.UserLevelProfile;

public interface UserLevelProfileRepository extends JpaRepository<UserLevelProfile, Long> {

    Optional<UserLevelProfile> findByConsumerId(Long consumerId);

    /**
     * Para el scheduler de inactividad:
     * trae perfiles cuya última actividad fue antes de la fecha dada
     * y que no tienen beneficios ya pausados.
     */
    @Query("""
        SELECT p FROM UserLevelProfile p
        WHERE p.lastActivityAt < :threshold
        AND p.benefitsPaused = false
        """)
    List<UserLevelProfile> findActiveProfilesInactiveSince(
            @Param("threshold") LocalDateTime threshold
    );

    /**
     * Para el scheduler de re-engagement (día 60):
     * perfiles pausados desde hace más de 60 días.
     */
    @Query("""
        SELECT p FROM UserLevelProfile p
        WHERE p.lastActivityAt < :threshold
        AND p.benefitsPaused = true
        """)
    List<UserLevelProfile> findPausedProfilesInactiveSince(
            @Param("threshold") LocalDateTime threshold
    );
}