package com.verygana2.services;

import java.time.LocalDateTime;
import java.util.Optional;

import com.verygana2.event.LevelUpEvent;
import com.verygana2.services.interfaces.levels.LevelService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.levels.LevelProfileResponse;
import com.verygana2.dtos.levels.TransactionLogResponse;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.levels.ReactivationMission;
import com.verygana2.models.levels.UserLevelProfile;
import com.verygana2.models.levels.XpKeyTransactionLog;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.levels.ReactivationMissionRepository;
import com.verygana2.repositories.levels.UserLevelProfileRepository;
import com.verygana2.repositories.levels.XpKeyTransactionLogRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LevelServiceImpl implements LevelService {

    private final UserLevelProfileRepository profileRepository;
    private final XpKeyTransactionLogRepository logRepository;
    private final ReactivationMissionRepository missionRepository;
    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Inicialización ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserLevelProfile initializeProfile(Long consumerId) {
        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "ConsumerDetails not found: " + consumerId));

        return profileRepository.findByConsumerId(consumerId)
                .orElseGet(() -> {
                    UserLevelProfile profile = UserLevelProfile.builder()
                            .consumer(consumer)
                            .xpTotal(0L)
                            .currentLevel(UserLevel.BRONCE)
                            .lastActivityAt(LocalDateTime.now())
                            .build();
                    log.info("Level profile initialized for consumer {}", consumerId);
                    return profileRepository.save(profile);
                });
    }

    // ─── Award XP ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserLevelProfile awardActivity(Long consumerId, ActivityType activityType) {
        UserLevelProfile profile = getOrCreateProfile(consumerId);

        // Sin multiplicador si los beneficios están pausados
        long xpEarned = profile.isBenefitsPaused()
                ? activityType.getXpBase()
                : profile.getCurrentLevel().applyMultiplier(activityType.getXpBase());

        applyXp(profile, xpEarned);
        saveTransactionLog(consumerId, activityType, xpEarned,
                profile.getCurrentLevel().getMultiplier());

        log.debug("Activity {} awarded to consumer {}: +{}xp", activityType, consumerId, xpEarned);

        return profileRepository.save(profile);
    }

    // ─── Multiplicador ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public double getMultiplier(Long consumerId) {
        return getOrCreateProfile(consumerId).getCurrentLevel().getMultiplier();
    }

    // ─── Inactividad ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void pauseBenefits(Long consumerId) {
        UserLevelProfile profile = getOrCreateProfile(consumerId);
        if (profile.isBenefitsPaused()) return;

        profile.setBenefitsPaused(true);
        profileRepository.save(profile);

        triggerReactivationMission(consumerId);
        log.info("Benefits paused for consumer {} due to inactivity", consumerId);
    }

    @Override
    @Transactional
    public void triggerReactivationMission(Long consumerId) {
        boolean alreadyActive = missionRepository
                .findByConsumerIdAndCompletedFalseAndExpiredFalse(consumerId)
                .isPresent();
        if (alreadyActive) return;

        ReactivationMission mission = ReactivationMission.builder()
                .consumerId(consumerId)
                .startedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .keysGoal(200L)
                .keysProgress(0L)
                .build();

        missionRepository.save(mission);

        UserLevelProfile profile = getOrCreateProfile(consumerId);
        profile.setReactivationMissionActive(true);
        profileRepository.save(profile);

        log.info("Reactivation mission created for consumer {}", consumerId);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public LevelProfileResponse getProfileResponse(Long consumerId) {
        UserLevelProfile profile = getOrCreateProfile(consumerId);

        ReactivationMission mission = missionRepository
                .findByConsumerIdAndCompletedFalseAndExpiredFalse(consumerId)
                .orElse(null);

        return new LevelProfileResponse(
                profile.getCurrentLevel(),
                profile.getXpTotal(),
                profile.getCurrentLevel().xpToNextLevel(profile.getXpTotal()),
                profile.getCurrentLevel().getMultiplier(),
                profile.isBenefitsPaused(),
                profile.isReactivationMissionActive(),
                mission != null ? mission.getKeysGoal()     : null,
                mission != null ? mission.getKeysProgress() : null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionLogResponse> getTransactionHistory(
            Long consumerId, Pageable pageable) {

        return logRepository
                .findByConsumerIdOrderByCreatedAtDesc(consumerId, pageable)
                .map(entry -> new TransactionLogResponse(
                        entry.getId(),
                        entry.getActivityType(),
                        entry.getXpEarned(),
                        entry.getMultiplierApplied(),
                        entry.getCreatedAt()
                ));
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private UserLevelProfile getOrCreateProfile(Long consumerId) {
        return profileRepository.findByConsumerId(consumerId)
                .orElseGet(() -> initializeProfile(consumerId));
    }

    /**
     * Aplica XP al perfil, actualiza lastActivityAt, evalúa subida de nivel
     * y avanza misión de reactivación si está activa.
     */
    private void applyXp(UserLevelProfile profile, long xpEarned) {
        profile.setXpTotal(profile.getXpTotal() + xpEarned);
        profile.setLastActivityAt(LocalDateTime.now());
        checkAndUpdateLevel(profile);

        if (profile.isReactivationMissionActive()) {
            updateReactivationProgress(profile, xpEarned);
        }
    }

    private void checkAndUpdateLevel(UserLevelProfile profile) {
        UserLevel newLevel = UserLevel.fromXp(profile.getXpTotal());
        if (newLevel != profile.getCurrentLevel()) {
            UserLevel previous = profile.getCurrentLevel();
            profile.setCurrentLevel(newLevel);
            Long consumerId = profile.getConsumer().getId();
            log.info("Consumer {} leveled up: {} → {}", consumerId, previous, newLevel);
            eventPublisher.publishEvent(
                    new LevelUpEvent(this, consumerId, previous, newLevel));
        }
    }

    private void updateReactivationProgress(UserLevelProfile profile, long xpEarned) {
        Optional<ReactivationMission> missionOpt = missionRepository
                .findByConsumerIdAndCompletedFalseAndExpiredFalse(
                        profile.getConsumer().getId());

        if (missionOpt.isEmpty()) {
            profile.setReactivationMissionActive(false);
            return;
        }

        ReactivationMission mission = missionOpt.get();
        mission.setKeysProgress(mission.getKeysProgress() + xpEarned);

        if (mission.getKeysProgress() >= mission.getKeysGoal()) {
            mission.setCompleted(true);
            profile.setBenefitsPaused(false);
            profile.setReactivationMissionActive(false);
            log.info("Reactivation mission completed for consumer {}", profile.getId());
        }

        missionRepository.save(mission);
    }

    private void saveTransactionLog(Long consumerId, ActivityType activityType,
                                    long xpEarned, double multiplier) {
        logRepository.save(XpKeyTransactionLog.builder()
                .consumerId(consumerId)
                .activityType(activityType)
                .xpEarned(xpEarned)
                .multiplierApplied(multiplier)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public UserLevel getUserLevel(Long consumerId) {
       UserLevelProfile profile = profileRepository.findByConsumerId(consumerId).orElseThrow(() -> new EntityNotFoundException("User profile not found for: " + consumerId));
       return profile.getCurrentLevel();
    }
}
