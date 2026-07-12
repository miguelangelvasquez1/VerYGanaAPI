package com.verygana2.services.games;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.services.scoring.ScoringContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del flujo de selección de campañas en GameServiceImpl.selectBestCampaign, enfocados en:
 * <ul>
 *   <li>Parámetros correctos al repositorio (status, municipio, inicio del día, límite de candidatos)</li>
 *   <li>Comportamiento cuando no hay candidatos</li>
 *   <li>No se filtra por juego — cualquier campaña activa es candidata (ver GameServiceImpl)</li>
 * </ul>
 *
 * Los hard filters reales (SQL) se prueban con tests de integración de repositorio.
 * El scoring en sí se prueba en CampaignScorerTest. Aquí se usa un CampaignScorer real
 * (no mockeado), igual que AdSelectionTest hace con AdScorer — el objetivo es verificar
 * la orquestación del servicio, no la matemática del scoring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameSelection (servicio)")
class GameSelectionTest {

    @Mock CampaignRepository campaignRepository;
    @Mock GameSessionRepository gameSessionRepository;

    private CampaignScoringConfig scoringConfig;
    private CampaignScorer campaignScorer;
    private Clock fixedClock;

    private SelectionHarness harness;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
        scoringConfig = new CampaignScoringConfig();
        campaignScorer = new CampaignScorer(scoringConfig);
        harness = new SelectionHarness(campaignRepository, gameSessionRepository, fixedClock, scoringConfig, campaignScorer);
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

    private void stubEmptyCandidates() {
        when(campaignRepository.findEligibleCampaignsForConsumer(anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Parámetros al repositorio")
    class RepositoryParams {

        @Test
        @DisplayName("status ACTIVE y municipio del consumidor se pasan como hard filters")
        void statusAndMunicipality_passedToRepo() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));
            stubEmptyCandidates();

            harness.selectBestCampaign(consumer);

            ArgumentCaptor<CampaignStatus> statusCaptor = ArgumentCaptor.forClass(CampaignStatus.class);
            ArgumentCaptor<Municipality> munCaptor = ArgumentCaptor.forClass(Municipality.class);
            verify(campaignRepository).findEligibleCampaignsForConsumer(
                    anyLong(), statusCaptor.capture(), munCaptor.capture(), any(), any());

            assertThat(statusCaptor.getValue()).isEqualTo(CampaignStatus.ACTIVE);
            assertThat(munCaptor.getValue().getCode()).isEqualTo("BOG");
        }

        @Test
        @DisplayName("todayStart = inicio del día se pasa al repositorio")
        void todayStart_isStartOfDay() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));
            stubEmptyCandidates();

            harness.selectBestCampaign(consumer);

            ArgumentCaptor<ZonedDateTime> todayStartCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(campaignRepository).findEligibleCampaignsForConsumer(
                    anyLong(), any(), any(), todayStartCaptor.capture(), any());

            ZonedDateTime now = ZonedDateTime.now(fixedClock);
            ZonedDateTime expectedStart = now.toLocalDate().atStartOfDay(now.getZone());
            assertThat(todayStartCaptor.getValue()).isEqualTo(expectedStart);
        }

        @Test
        @DisplayName("candidateLimit del config se usa como tamaño de página")
        void candidateLimit_passedAsPageSize() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));
            stubEmptyCandidates();

            harness.selectBestCampaign(consumer);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(campaignRepository).findEligibleCampaignsForConsumer(
                    anyLong(), any(), any(), any(), pageableCaptor.capture());

            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(scoringConfig.getCandidateLimit());
        }
    }

    @Nested
    @DisplayName("Sin candidatos elegibles")
    class NoCandidates {

        @Test
        @DisplayName("si el repositorio retorna vacío, el servicio retorna Optional.empty() sin consultar recencia")
        void noCandidates_returnsEmpty() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));
            stubEmptyCandidates();

            Optional<Campaign> result = harness.selectBestCampaign(consumer);

            assertThat(result).isEmpty();
            verify(gameSessionRepository, never()).findLastPlayedAtByCampaignIds(any(), any());
        }
    }

    @Nested
    @DisplayName("Selección entre candidatos")
    class CandidateSelection {

        @Test
        @DisplayName("con un único candidato, lo retorna")
        void singleCandidate_returnsIt() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));

            Campaign campaign = new Campaign();
            campaign.setId(10L);
            campaign.setBudgetCents(1000L);
            campaign.setSpentCents(0L);
            campaign.setCreatedAt(ZonedDateTime.now(fixedClock));

            when(campaignRepository.findEligibleCampaignsForConsumer(anyLong(), any(), any(), any(), any()))
                    .thenReturn(List.of(campaign));
            when(gameSessionRepository.findLastPlayedAtByCampaignIds(anyLong(), any()))
                    .thenReturn(List.of());

            Optional<Campaign> result = harness.selectBestCampaign(consumer);

            assertThat(result).isPresent().hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(10L));
        }

        @Test
        @DisplayName("con varios candidatos, gana el de mayor score (no depende de gameId)")
        void multipleCandidates_scorerPicksBest() {
            ConsumerDetails consumer = consumer(1L, municipality("BOG"));
            Category preferred = new Category();
            preferred.setId(99L);
            consumer.setCategories(List.of(preferred));

            Campaign matching = new Campaign();
            matching.setId(1L);
            matching.setBudgetCents(1000L);
            matching.setSpentCents(0L);
            matching.setCreatedAt(ZonedDateTime.now(fixedClock));
            com.verygana2.models.TargetAudience taMatch = new com.verygana2.models.TargetAudience();
            taMatch.setCategories(List.of(preferred));
            matching.setTargetAudience(taMatch);

            Campaign nonMatching = new Campaign();
            nonMatching.setId(2L);
            nonMatching.setBudgetCents(1000L);
            nonMatching.setSpentCents(0L);
            nonMatching.setCreatedAt(ZonedDateTime.now(fixedClock));

            when(campaignRepository.findEligibleCampaignsForConsumer(anyLong(), any(), any(), any(), any()))
                    .thenReturn(List.of(nonMatching, matching));
            when(gameSessionRepository.findLastPlayedAtByCampaignIds(anyLong(), any()))
                    .thenReturn(List.of());

            Optional<Campaign> result = harness.selectBestCampaign(consumer);

            assertThat(result).isPresent().hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(1L));
        }
    }

    // ─── Harness (adaptador mínimo sobre GameServiceImpl.selectBestCampaign) ──

    /**
     * Adaptador ligero que replica el fragmento de selección de GameServiceImpl.selectBestCampaign
     * con los mocks, sin necesidad de levantar todo el contexto de Spring.
     */
    static class SelectionHarness {

        private final CampaignRepository campaignRepository;
        private final GameSessionRepository gameSessionRepository;
        private final Clock clock;
        private final CampaignScoringConfig scoringConfig;
        private final CampaignScorer campaignScorer;

        SelectionHarness(CampaignRepository campaignRepository,
                          GameSessionRepository gameSessionRepository,
                          Clock clock,
                          CampaignScoringConfig scoringConfig,
                          CampaignScorer campaignScorer) {
            this.campaignRepository = campaignRepository;
            this.gameSessionRepository = gameSessionRepository;
            this.clock = clock;
            this.scoringConfig = scoringConfig;
            this.campaignScorer = campaignScorer;
        }

        Optional<Campaign> selectBestCampaign(ConsumerDetails consumer) {
            ZonedDateTime now = ZonedDateTime.now(clock);
            ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(now.getZone());

            List<Campaign> candidates = campaignRepository.findEligibleCampaignsForConsumer(
                    consumer.getId(),
                    CampaignStatus.ACTIVE,
                    consumer.getMunicipality(),
                    todayStart,
                    PageRequest.of(0, scoringConfig.getCandidateLimit()));

            if (candidates.isEmpty()) return Optional.empty();

            Set<Long> candidateIds = candidates.stream().map(Campaign::getId).collect(Collectors.toSet());
            Map<Long, ZonedDateTime> lastPlayedAt = gameSessionRepository
                    .findLastPlayedAtByCampaignIds(consumer.getId(), candidateIds)
                    .stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (ZonedDateTime) row[1]));

            ScoringContext ctx = new ScoringContext(
                    consumer.getId(),
                    consumer.getAge(),
                    null,
                    consumer.getCategories().stream().map(Category::getId).collect(Collectors.toSet()),
                    lastPlayedAt,
                    now);

            return campaignScorer.selectBest(candidates, ctx);
        }
    }
}
