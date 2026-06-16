package com.verygana2.repositories.levels;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.levels.ReactivationMission;

public interface ReactivationMissionRepository
        extends JpaRepository<ReactivationMission, Long> {

    Optional<ReactivationMission> findByConsumerIdAndCompletedFalseAndExpiredFalse(
            Long consumerId
    );

    // Para el scheduler que expira misiones vencidas
    List<ReactivationMission> findByCompletedFalseAndExpiredFalseAndExpiresAtBefore(
            LocalDateTime now
    );
}