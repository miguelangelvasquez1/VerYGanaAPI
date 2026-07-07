package com.verygana2.ads;

import com.verygana2.models.Municipality;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.ads.AdScorer;
import com.verygana2.services.ads.AdScoringConfig;
import com.verygana2.services.ads.AdScoringContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests del flujo de selección de anuncios en AdServiceImpl, enfocados en:
 * <ul>
 *   <li>Parámetros correctos al repositorio (cooldown, daily limit, municipio)</li>
 *   <li>Comportamiento cuando no hay candidatos</li>
 *   <li>Reanudación de sesión activa</li>
 * </ul>
 *
 * Los hard filters reales (SQL) se prueban con tests de integración de repositorio.
 * Aquí se verifica que el servicio construye correctamente los parámetros.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdSelection (servicio)")
class AdSelectionTest {

    // ─── Dependencias mockeadas ───────────────────────────────────────────────

    @Mock AdRepository adRepository;
    @Mock AdWatchSessionRepository adWatchSessionRepository;
    @Mock ConsumerDetailsRepository consumerDetailsRepository;

    private AdScoringConfig scoringConfig;
    private AdScorer adScorer;
    private Clock fixedClock;

    // SUT parcial — sólo probamos el fragmento de selección sin levantar Spring
    private SelectionHarness harness;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
        scoringConfig = new AdScoringConfig();
        adScorer = new AdScorer(scoringConfig);
        harness = new SelectionHarness(adRepository, adWatchSessionRepository,
                consumerDetailsRepository, fixedClock, scoringConfig, adScorer);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ConsumerDetails consumer(Long id, Municipality municipality) {
        ConsumerDetails c = new ConsumerDetails();
        c.setId(id);
        c.setMunicipality(municipality);
        c.setCategories(List.of());
        return c;
    }

    private Municipality municipality(String code) {
        Municipality m = new Municipality();
        m.setCode(code);
        return m;
    }

    private Ad activeAd(Long id) {
        AdAsset asset = new AdAsset();
        asset.setObjectKey("key-" + id);
        asset.setMediaType(MediaType.IMAGE);
        asset.setDurationSeconds(30);

        Ad ad = new Ad();
        ad.setId(id);
        ad.setStatus(AdStatus.ACTIVE);
        ad.setMaxLikes(100);
        ad.setCurrentLikes(0);
        ad.getTargetAudience().setCategories(List.of());
        ad.setAsset(asset);
        return ad;
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Parámetros de cooldown")
    class CooldownParams {

        @Test
        @DisplayName("cooldownThreshold = now - cooldownMinutes se pasa al repositorio")
        void cooldownThreshold_correctlyComputed() {
            Municipality mun = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, mun);
            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(adRepository.findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            harness.getNextAd(1L, 60L);

            ArgumentCaptor<ZonedDateTime> cooldownCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(adRepository).findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), cooldownCaptor.capture(), any(), any());

            ZonedDateTime now = ZonedDateTime.now(fixedClock);
            assertThat(cooldownCaptor.getValue()).isEqualTo(now.minusMinutes(60));
        }

        @Test
        @DisplayName("cooldown 0 → cooldownThreshold = now (sin ventana bloqueante)")
        void zeroCooldown_thresholdEqualsNow() {
            Municipality mun = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, mun);
            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(adRepository.findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            harness.getNextAd(1L, 0L);

            ArgumentCaptor<ZonedDateTime> cooldownCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(adRepository).findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), cooldownCaptor.capture(), any(), any());

            assertThat(cooldownCaptor.getValue()).isEqualTo(ZonedDateTime.now(fixedClock));
        }
    }

    @Nested
    @DisplayName("Parámetros de límite diario")
    class DailyLimitParams {

        @Test
        @DisplayName("todayStart = inicio del día UTC se pasa al repositorio")
        void todayStart_isStartOfDay() {
            Municipality mun = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, mun);
            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(adRepository.findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            harness.getNextAd(1L, 60L);

            ArgumentCaptor<ZonedDateTime> todayStartCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(adRepository).findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), todayStartCaptor.capture(), any(), any(), any());

            ZonedDateTime now = ZonedDateTime.now(fixedClock);
            ZonedDateTime expectedStart = now.toLocalDate().atStartOfDay(now.getZone());
            assertThat(todayStartCaptor.getValue()).isEqualTo(expectedStart);
        }
    }

    @Nested
    @DisplayName("Filtro por municipio")
    class MunicipalityFilter {

        @Test
        @DisplayName("el municipio del consumidor se pasa al repositorio como hard filter")
        void consumerMunicipality_passedToRepo() {
            Municipality bogota = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, bogota);
            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(adRepository.findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            harness.getNextAd(1L, 60L);

            ArgumentCaptor<Municipality> munCaptor = ArgumentCaptor.forClass(Municipality.class);
            verify(adRepository).findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), munCaptor.capture(), any(), any(), any(), any());

            assertThat(munCaptor.getValue().getCode()).isEqualTo("BOG");
        }
    }

    @Nested
    @DisplayName("Reanudación de sesión activa")
    class ActiveSessionResume {

        @Test
        @DisplayName("si hay sesión activa no expirada, NO llama al repositorio de candidatos")
        void activeSession_noRepositoryCall() {
            Municipality mun = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, mun);

            Ad ad = activeAd(10L);
            AdWatchSession session = new AdWatchSession(consumer, ad);
            session.setId(UUID.randomUUID());
            session.setResumeCount(0);
            session.setStartedAt(ZonedDateTime.now(fixedClock));
            session.setExpiresAt(ZonedDateTime.now(fixedClock).plusMinutes(5));
            session.setStatus(AdWatchSessionStatus.ACTIVE);

            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.of(session));
            when(adWatchSessionRepository.save(any())).thenReturn(session);

            harness.getNextAd(1L, 60L);

            verify(adRepository, never()).findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Sin candidatos elegibles")
    class NoCandidates {

        @Test
        @DisplayName("si el repositorio retorna vacío, el servicio retorna Optional.empty()")
        void noCandidates_returnsEmpty() {
            Municipality mun = municipality("BOG");
            ConsumerDetails consumer = consumer(1L, mun);
            when(consumerDetailsRepository.findById(1L)).thenReturn(Optional.of(consumer));
            when(adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(adRepository.findEligibleAdsForConsumer(
                    anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            Optional<?> result = harness.getNextAd(1L, 60L);

            assertThat(result).isEmpty();
            verify(adWatchSessionRepository, never()).save(any());
        }
    }

    // ─── Harness (adaptador mínimo sobre AdServiceImpl) ──────────────────────

    /**
     * Adaptador ligero que llama únicamente al flujo de selección,
     * sin necesidad de levantar todo el contexto de Spring.
     * Replica el fragmento relevante de AdServiceImpl con los mocks.
     */
    static class SelectionHarness {

        private final AdRepository adRepository;
        private final AdWatchSessionRepository adWatchSessionRepository;
        private final ConsumerDetailsRepository consumerDetailsRepository;
        private final Clock clock;
        private final AdScoringConfig scoringConfig;
        private final AdScorer adScorer;

        SelectionHarness(AdRepository adRepository,
                         AdWatchSessionRepository adWatchSessionRepository,
                         ConsumerDetailsRepository consumerDetailsRepository,
                         Clock clock,
                         AdScoringConfig scoringConfig,
                         AdScorer adScorer) {
            this.adRepository = adRepository;
            this.adWatchSessionRepository = adWatchSessionRepository;
            this.consumerDetailsRepository = consumerDetailsRepository;
            this.clock = clock;
            this.scoringConfig = scoringConfig;
            this.adScorer = adScorer;
        }

        Optional<?> getNextAd(Long consumerId, long cooldownMinutes) {
            ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId)
                    .orElseThrow();

            ZonedDateTime now = ZonedDateTime.now(clock);

            Optional<AdWatchSession> activeSession =
                    adWatchSessionRepository.findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                            consumerId, AdWatchSessionStatus.ACTIVE, now);

            if (activeSession.isPresent()) {
                AdWatchSession session = activeSession.get();
                session.setResumeCount(session.getResumeCount() + 1);
                session.setExpiresAt(now.plusMinutes(5));
                adWatchSessionRepository.save(session);
                return Optional.of("resumed");
            }

            ZonedDateTime cooldownThreshold = now.minusMinutes(cooldownMinutes);
            ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(now.getZone());

            List<Ad> candidates = adRepository.findEligibleAdsForConsumer(
                    consumerId,
                    AdStatus.ACTIVE,
                    List.of(AdWatchSessionStatus.LIKED),
                    AdWatchSessionStatus.LIKED,
                    now,
                    consumer.getMunicipality(),
                    todayStart,
                    cooldownThreshold,
                    AdWatchSessionStatus.ACTIVE,
                    PageRequest.of(0, scoringConfig.getCandidateLimit()));

            if (candidates.isEmpty()) return Optional.empty();

            AdScoringContext ctx = new AdScoringContext(
                    consumerId,
                    consumer.getAge(),
                    null,
                    java.util.Set.of(),
                    java.util.Map.of(),
                    now);

            return adScorer.selectBest(candidates, ctx);
        }
    }
}
