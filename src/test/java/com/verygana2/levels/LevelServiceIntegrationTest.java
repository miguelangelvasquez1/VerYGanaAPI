package com.verygana2.levels;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.verygana2.dtos.levels.LevelProfileResponse;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.levels.ReactivationMission;
import com.verygana2.models.levels.UserLevelProfile;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.levels.ReactivationMissionRepository;
import com.verygana2.repositories.levels.UserLevelProfileRepository;
import com.verygana2.repositories.levels.XpKeyTransactionLogRepository;
import com.verygana2.services.LevelServiceImpl;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Test de integración del sistema de niveles contra H2 (modo MySQL).
 * Carga solo el slice JPA + LevelServiceImpl; NotificationService va mockeado.
 */
@DataJpaTest(properties = {
        // Perfil vacío: evita cargar application-dev.yml (llaves RSA, R2, etc.)
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:levels-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LevelServiceImpl.class)
@DisplayName("LevelService (integración H2)")
class LevelServiceIntegrationTest {

    @Autowired EntityManager em;
    @Autowired LevelServiceImpl levelService;
    @Autowired UserLevelProfileRepository profileRepository;
    @Autowired XpKeyTransactionLogRepository logRepository;
    @Autowired ReactivationMissionRepository missionRepository;

    @MockitoBean NotificationService notificationService;

    private ConsumerDetails consumer;

    @BeforeEach
    void setUp() {
        consumer = TestEntities.persistConsumer(em);
    }

    @Test
    @DisplayName("initializeProfile crea el perfil BRONCE persistido y es idempotente")
    void initializeProfileIsIdempotent() {
        UserLevelProfile first = levelService.initializeProfile(consumer.getId());
        UserLevelProfile second = levelService.initializeProfile(consumer.getId());

        assertThat(first.getId()).isEqualTo(consumer.getId());
        assertThat(first.getCurrentLevel()).isEqualTo(UserLevel.BRONCE);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(profileRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("awardActivity acumula XP con multiplicador y persiste el log")
    void awardActivityAccumulatesXpAndPersistsLog() {
        levelService.initializeProfile(consumer.getId());

        // BRONCE: SURVEY 30 × 0.5 = 15, dos veces = 30
        levelService.awardActivity(consumer.getId(), ActivityType.SURVEY_COMPLETED);
        levelService.awardActivity(consumer.getId(), ActivityType.SURVEY_COMPLETED);
        em.flush();

        UserLevelProfile profile = profileRepository.findByConsumerId(consumer.getId()).orElseThrow();
        assertThat(profile.getXpTotal()).isEqualTo(30L);
        assertThat(logRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("cruza a PLATA al superar 1000 XP")
    void levelsUpToPlataCrossingThreshold() {
        UserLevelProfile profile = levelService.initializeProfile(consumer.getId());
        profile.setXpTotal(995L);
        profileRepository.saveAndFlush(profile);

        // 30 × 0.5 = 15 → 1010 XP
        levelService.awardActivity(consumer.getId(), ActivityType.SURVEY_COMPLETED);
        em.flush();

        UserLevelProfile updated = profileRepository.findByConsumerId(consumer.getId()).orElseThrow();
        assertThat(updated.getXpTotal()).isEqualTo(1010L);
        assertThat(updated.getCurrentLevel()).isEqualTo(UserLevel.PLATA);
    }

    @Test
    @DisplayName("pauseBenefits persiste la pausa, crea la misión y notifica")
    void pauseBenefitsPersistsPauseAndMission() {
        levelService.initializeProfile(consumer.getId());

        levelService.pauseBenefits(consumer.getId());
        em.flush();

        UserLevelProfile profile = profileRepository.findByConsumerId(consumer.getId()).orElseThrow();
        assertThat(profile.isBenefitsPaused()).isTrue();
        assertThat(profile.isReactivationMissionActive()).isTrue();

        ReactivationMission mission = missionRepository
                .findByConsumerIdAndCompletedFalseAndExpiredFalse(consumer.getId())
                .orElseThrow();
        assertThat(mission.getXpGoal()).isEqualTo(200L);
        assertThat(mission.getXpProgress()).isZero();

        org.mockito.Mockito.verify(notificationService).createInternalNotification(
                org.mockito.ArgumentMatchers.eq(consumer.getId()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("completar la misión de reactivación restaura los beneficios")
    void completingMissionRestoresBenefits() {
        levelService.initializeProfile(consumer.getId());
        levelService.pauseBenefits(consumer.getId());
        em.flush();

        // Dejar la misión a un paso de completarse
        ReactivationMission mission = missionRepository
                .findByConsumerIdAndCompletedFalseAndExpiredFalse(consumer.getId())
                .orElseThrow();
        mission.setXpProgress(190L);
        missionRepository.saveAndFlush(mission);

        // Pausado gana como BRONCE: 30 × 0.5 = 15 → 205 ≥ 200
        levelService.awardActivity(consumer.getId(), ActivityType.SURVEY_COMPLETED);
        em.flush();

        UserLevelProfile profile = profileRepository.findByConsumerId(consumer.getId()).orElseThrow();
        assertThat(profile.isBenefitsPaused()).isFalse();
        assertThat(profile.isReactivationMissionActive()).isFalse();
        assertThat(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(
                consumer.getId())).isEmpty();
    }

    @Test
    @DisplayName("getProfileResponse expone la misión activa con meta y progreso de XP")
    void profileResponseExposesActiveMission() {
        levelService.initializeProfile(consumer.getId());
        levelService.pauseBenefits(consumer.getId());
        em.flush();

        LevelProfileResponse response = levelService.getProfileResponse(consumer.getId());

        assertThat(response.benefitsPaused()).isTrue();
        assertThat(response.reactivationMissionActive()).isTrue();
        assertThat(response.reactivationXpGoal()).isEqualTo(200L);
        assertThat(response.reactivationXpProgress()).isZero();
    }

    @Test
    @DisplayName("getMultiplier refleja la pausa contra datos reales")
    void multiplierReflectsPauseState() {
        UserLevelProfile profile = levelService.initializeProfile(consumer.getId());
        profile.setCurrentLevel(UserLevel.DIAMANTE);
        profileRepository.saveAndFlush(profile);

        assertThat(levelService.getMultiplier(consumer.getId()))
                .isEqualTo(UserLevel.DIAMANTE.getMultiplier());

        levelService.pauseBenefits(consumer.getId());
        em.flush();

        assertThat(levelService.getMultiplier(consumer.getId()))
                .isEqualTo(UserLevel.BRONCE.getMultiplier());
    }
}
