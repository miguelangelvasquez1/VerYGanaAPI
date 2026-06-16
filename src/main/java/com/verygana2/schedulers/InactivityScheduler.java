package com.verygana2.schedulers;

import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.services.interfaces.levels.LevelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.models.levels.ReactivationMission;
import com.verygana2.models.levels.UserLevelProfile;
import com.verygana2.repositories.levels.ReactivationMissionRepository;
import com.verygana2.repositories.levels.UserLevelProfileRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Jobs nocturnos de inactividad — corren a las 2:00 AM todos los días.
 * Requiere @EnableScheduling en tu clase principal o en una @Configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InactivityScheduler {

    private final UserLevelProfileRepository profileRepository;
    private final ReactivationMissionRepository missionRepository;
    private final LevelService levelService;

    /**
     * Día 31: pausa beneficios de usuarios inactivos.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void pauseInactiveUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(31);
        List<UserLevelProfile> toProcess =
                profileRepository.findActiveProfilesInactiveSince(threshold);

        log.info("Inactivity check: {} profiles to pause", toProcess.size());

        for (UserLevelProfile profile : toProcess) {
            try {
                levelService.pauseBenefits(profile.getId());
            } catch (Exception e) {
                log.error("Error pausing benefits for consumer {}: {}",
                        profile.getId(), e.getMessage());
            }
        }
    }

    /**
     * Expira misiones de reactivación vencidas (7 días sin completar).
     */
    @Scheduled(cron = "0 5 2 * * *")
    public void expireReactivationMissions() {
        List<ReactivationMission> expired =
                missionRepository.findByCompletedFalseAndExpiredFalseAndExpiresAtBefore(
                        LocalDateTime.now()
                );

        log.info("Expiring {} reactivation missions", expired.size());

        for (ReactivationMission mission : expired) {
            mission.setExpired(true);
            missionRepository.save(mission);

            // Si expiró sin completarse, el perfil sigue pausado
            // (el usuario necesita reiniciar otra misión volviendo a la app)
            profileRepository.findByConsumerId(mission.getConsumerId())
                    .ifPresent(p -> {
                        p.setReactivationMissionActive(false);
                        profileRepository.save(p);
                    });
        }
    }

    /**
     * Día 60: re-engagement por email (dispara evento/notificación).
     * La lógica de envío de email la manejas en tu NotificationService existente.
     */
    @Scheduled(cron = "0 10 2 * * *")
    public void reEngagementCampaign() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(60);
        List<UserLevelProfile> longInactive =
                profileRepository.findPausedProfilesInactiveSince(threshold);

        log.info("Re-engagement campaign: {} users", longInactive.size());
        // TODO: publicar evento para que tu NotificationService envíe email
        // con oferta especial de llaves
    }
}
