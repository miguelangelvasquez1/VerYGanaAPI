package com.verygana2.levels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.event.LevelUpEvent;
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
import com.verygana2.services.LevelServiceImpl;
import com.verygana2.services.interfaces.NotificationService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("LevelServiceImpl")
class LevelServiceImplTest {

    private static final Long CONSUMER_ID = 42L;

    @Mock UserLevelProfileRepository profileRepository;
    @Mock XpKeyTransactionLogRepository logRepository;
    @Mock ReactivationMissionRepository missionRepository;
    @Mock ConsumerDetailsRepository consumerDetailsRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock NotificationService notificationService;

    @InjectMocks LevelServiceImpl service;

    private ConsumerDetails consumer;

    @BeforeEach
    void setUp() {
        consumer = new ConsumerDetails();
        consumer.setId(CONSUMER_ID);
    }

    private UserLevelProfile profileWith(long xp, UserLevel level, boolean paused) {
        return UserLevelProfile.builder()
                .id(CONSUMER_ID)
                .consumer(consumer)
                .xpTotal(xp)
                .currentLevel(level)
                .benefitsPaused(paused)
                .lastActivityAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private void stubProfile(UserLevelProfile profile) {
        when(profileRepository.findByConsumerId(CONSUMER_ID)).thenReturn(Optional.of(profile));
    }

    // ─── initializeProfile ────────────────────────────────────────────────────

    @Nested
    @DisplayName("initializeProfile")
    class InitializeProfile {

        @Test
        @DisplayName("lanza EntityNotFoundException si el consumer no existe")
        void throwsWhenConsumerMissing() {
            when(consumerDetailsRepository.findById(CONSUMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initializeProfile(CONSUMER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("retorna el perfil existente sin crear uno nuevo")
        void returnsExistingProfile() {
            UserLevelProfile existing = profileWith(500, UserLevel.BRONCE, false);
            when(consumerDetailsRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            stubProfile(existing);

            UserLevelProfile result = service.initializeProfile(CONSUMER_ID);

            assertThat(result).isSameAs(existing);
            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("crea perfil BRONCE con 0 XP si no existe")
        void createsNewProfileAtBronce() {
            when(consumerDetailsRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(profileRepository.findByConsumerId(CONSUMER_ID)).thenReturn(Optional.empty());
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserLevelProfile result = service.initializeProfile(CONSUMER_ID);

            assertThat(result.getXpTotal()).isZero();
            assertThat(result.getCurrentLevel()).isEqualTo(UserLevel.BRONCE);
            assertThat(result.isBenefitsPaused()).isFalse();
        }
    }

    // ─── awardActivity ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("awardActivity")
    class AwardActivity {

        @BeforeEach
        void stubSave() {
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("aplica el multiplicador del nivel actual (ORO: 30 × 0.7 = 21)")
        void appliesCurrentLevelMultiplier() {
            stubProfile(profileWith(5000, UserLevel.ORO, false));

            UserLevelProfile result = service.awardActivity(CONSUMER_ID, ActivityType.SURVEY_COMPLETED);

            assertThat(result.getXpTotal()).isEqualTo(5000 + 21);
        }

        @Test
        @DisplayName("pausado gana con multiplicador BRONCE aunque sea DIAMANTE")
        void pausedEarnsAsBronce() {
            stubProfile(profileWith(40000, UserLevel.DIAMANTE, true));

            UserLevelProfile result = service.awardActivity(CONSUMER_ID, ActivityType.SURVEY_COMPLETED);

            // 30 × 0.5 (BRONCE) = 15, no 30 × 1.0
            assertThat(result.getXpTotal()).isEqualTo(40000 + 15);
        }

        @Test
        @DisplayName("guarda el log con el multiplicador efectivo aplicado")
        void savesLogWithEffectiveMultiplier() {
            stubProfile(profileWith(40000, UserLevel.DIAMANTE, true));

            service.awardActivity(CONSUMER_ID, ActivityType.VIDEO_WATCHED);

            ArgumentCaptor<XpKeyTransactionLog> captor =
                    ArgumentCaptor.forClass(XpKeyTransactionLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getMultiplierApplied())
                    .isEqualTo(UserLevel.BRONCE.getMultiplier());
            assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.VIDEO_WATCHED);
        }

        @Test
        @DisplayName("publica LevelUpEvent al cruzar el umbral de nivel")
        void publishesLevelUpEventOnThresholdCross() {
            // 990 XP + 20 (GAME_PLAYED × 0.5 = 10) = 1000 → PLATA
            stubProfile(profileWith(990, UserLevel.BRONCE, false));

            service.awardActivity(CONSUMER_ID, ActivityType.GAME_PLAYED);

            ArgumentCaptor<LevelUpEvent> captor = ArgumentCaptor.forClass(LevelUpEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            LevelUpEvent event = captor.getValue();
            assertThat(event.getNewLevel()).isEqualTo(UserLevel.PLATA);
            assertThat(event.getPreviousLevel()).isEqualTo(UserLevel.BRONCE);
        }

        @Test
        @DisplayName("NO publica evento si no hay cambio de nivel")
        void noEventWithoutLevelChange() {
            stubProfile(profileWith(100, UserLevel.BRONCE, false));

            service.awardActivity(CONSUMER_ID, ActivityType.VIDEO_WATCHED);

            verify(eventPublisher, never())
                    .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        }

        @Test
        @DisplayName("actualiza lastActivityAt")
        void updatesLastActivity() {
            UserLevelProfile profile = profileWith(100, UserLevel.BRONCE, false);
            LocalDateTime before = profile.getLastActivityAt();
            stubProfile(profile);

            service.awardActivity(CONSUMER_ID, ActivityType.VIDEO_WATCHED);

            assertThat(profile.getLastActivityAt()).isAfter(before);
        }
    }

    // ─── getMultiplier ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMultiplier")
    class GetMultiplier {

        @Test
        @DisplayName("retorna el multiplicador del nivel actual")
        void returnsCurrentLevelMultiplier() {
            stubProfile(profileWith(5000, UserLevel.ORO, false));

            assertThat(service.getMultiplier(CONSUMER_ID))
                    .isEqualTo(UserLevel.ORO.getMultiplier());
        }

        @Test
        @DisplayName("pausado retorna el multiplicador de BRONCE")
        void pausedReturnsBronceMultiplier() {
            stubProfile(profileWith(40000, UserLevel.DIAMANTE, true));

            assertThat(service.getMultiplier(CONSUMER_ID))
                    .isEqualTo(UserLevel.BRONCE.getMultiplier());
        }
    }

    // ─── pauseBenefits ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pauseBenefits")
    class PauseBenefits {

        @Test
        @DisplayName("no hace nada si ya estaba pausado")
        void idempotentWhenAlreadyPaused() {
            stubProfile(profileWith(5000, UserLevel.ORO, true));

            service.pauseBenefits(CONSUMER_ID);

            verify(profileRepository, never()).save(any());
            verify(missionRepository, never()).save(any());
            verify(notificationService, never())
                    .createInternalNotification(anyLong(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("pausa, crea misión con meta de 200 XP y notifica al usuario")
        void pausesCreatesMissionAndNotifies() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, false);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.empty());

            service.pauseBenefits(CONSUMER_ID);

            assertThat(profile.isBenefitsPaused()).isTrue();
            assertThat(profile.isReactivationMissionActive()).isTrue();

            ArgumentCaptor<ReactivationMission> captor =
                    ArgumentCaptor.forClass(ReactivationMission.class);
            verify(missionRepository).save(captor.capture());
            assertThat(captor.getValue().getXpGoal()).isEqualTo(200L);
            assertThat(captor.getValue().getXpProgress()).isZero();

            verify(notificationService).createInternalNotification(
                    org.mockito.ArgumentMatchers.eq(CONSUMER_ID), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("un fallo en la notificación NO rompe la pausa")
        void notificationFailureDoesNotBreakPause() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, false);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("SMTP down"))
                    .when(notificationService)
                    .createInternalNotification(anyLong(), anyString(), anyString(), any());

            service.pauseBenefits(CONSUMER_ID);

            assertThat(profile.isBenefitsPaused()).isTrue();
        }

        @Test
        @DisplayName("no duplica misión si ya hay una activa")
        void doesNotDuplicateActiveMission() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, false);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.of(ReactivationMission.builder().build()));

            service.pauseBenefits(CONSUMER_ID);

            verify(missionRepository, never()).save(any());
        }
    }

    // ─── Misión de reactivación (vía awardActivity) ───────────────────────────

    @Nested
    @DisplayName("misión de reactivación")
    class ReactivationMissionProgress {

        @Test
        @DisplayName("el XP ganado avanza el progreso de la misión")
        void xpAdvancesMissionProgress() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, true);
            profile.setReactivationMissionActive(true);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReactivationMission mission = ReactivationMission.builder()
                    .consumerId(CONSUMER_ID)
                    .xpGoal(200L)
                    .xpProgress(0L)
                    .startedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.of(mission));

            // SURVEY pausado: 30 × 0.5 = 15
            service.awardActivity(CONSUMER_ID, ActivityType.SURVEY_COMPLETED);

            assertThat(mission.getXpProgress()).isEqualTo(15L);
            assertThat(mission.isCompleted()).isFalse();
            assertThat(profile.isBenefitsPaused()).isTrue();
        }

        @Test
        @DisplayName("al alcanzar la meta: misión completada y beneficios restaurados")
        void reachingGoalCompletesAndUnpauses() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, true);
            profile.setReactivationMissionActive(true);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReactivationMission mission = ReactivationMission.builder()
                    .consumerId(CONSUMER_ID)
                    .xpGoal(200L)
                    .xpProgress(190L)
                    .startedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.of(mission));

            service.awardActivity(CONSUMER_ID, ActivityType.SURVEY_COMPLETED);

            assertThat(mission.isCompleted()).isTrue();
            assertThat(profile.isBenefitsPaused()).isFalse();
            assertThat(profile.isReactivationMissionActive()).isFalse();
        }

        @Test
        @DisplayName("si la misión desapareció, limpia el flag del perfil")
        void clearsFlagWhenMissionGone() {
            UserLevelProfile profile = profileWith(5000, UserLevel.ORO, false);
            profile.setReactivationMissionActive(true);
            stubProfile(profile);
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.findByConsumerIdAndCompletedFalseAndExpiredFalse(CONSUMER_ID))
                    .thenReturn(Optional.empty());

            service.awardActivity(CONSUMER_ID, ActivityType.VIDEO_WATCHED);

            assertThat(profile.isReactivationMissionActive()).isFalse();
        }
    }

    // ─── getUserLevel ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserLevel")
    class GetUserLevel {

        @Test
        @DisplayName("retorna el nivel del perfil")
        void returnsProfileLevel() {
            stubProfile(profileWith(10000, UserLevel.RUBI, false));

            assertThat(service.getUserLevel(CONSUMER_ID)).isEqualTo(UserLevel.RUBI);
        }

        @Test
        @DisplayName("lanza EntityNotFoundException si no hay perfil")
        void throwsWhenProfileMissing() {
            when(profileRepository.findByConsumerId(CONSUMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserLevel(CONSUMER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}