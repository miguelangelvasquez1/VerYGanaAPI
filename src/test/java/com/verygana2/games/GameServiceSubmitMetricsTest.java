package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.MetricType;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.games.GameServiceImpl;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.testsupport.KeyLedgerStubs;
import com.verygana2.utils.audit.AuditConfig;
import com.verygana2.utils.concurrency.RetryOnConcurrencyConflict;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl.submitGameMetrics (spec 013)")
class GameServiceSubmitMetricsTest {

    private static final String TOKEN = "token-secret-1";
    private static final String HASH = "hash-secret-1";

    @Mock GameRepository gameRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock BrandingRequestRepository brandingRequestRepository;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock KeyWalletService keyWalletService;
    @Mock KeyLedgerService keyLedgerService;
    @Mock LevelService levelService;

    @InjectMocks GameServiceImpl service;

    private GameSession session;
    private GameEventDTO<List<GameMetricDTO>> event;

    @BeforeEach
    void setUp() {
        KeyLedgerStubs.applyToWallet(keyLedgerService);
        ReflectionTestUtils.setField(service, "sessionExpirationTime", 30);
        ReflectionTestUtils.setField(service, "keyValueCents", 1_000L);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(42L);

        session = new GameSession();
        session.setId(99L);
        session.setSessionToken(TOKEN);
        session.setUserHash(HASH);
        session.setConsumer(consumer);
        session.setStartTime(ZonedDateTime.now().minusMinutes(2));
        session.setCompleted(false);

        event = eventWith(performance(3));
    }

    private GameEventDTO<List<GameMetricDTO>> eventWith(GameMetricDTO... metrics) {
        GameEventDTO<List<GameMetricDTO>> e = new GameEventDTO<>();
        e.setSessionToken(TOKEN);
        e.setUserHash(HASH);
        e.setPayload(List.of(metrics));
        return e;
    }

    private static GameMetricDTO performance(Object value) {
        return new GameMetricDTO("performance", MetricType.DOUBLE, value);
    }

    /** Campaña de los seeds: completar 5.000, factor 1, tope 20.000, presupuesto 100.000. */
    private Campaign campaignWith(CampaignStatus status, long spentCents) {
        return Campaign.builder()
                .id(7L)
                .status(status)
                .budgetCents(100_000L)
                .spentCents(spentCents)
                .completionRewardCents(5_000L)
                .scoreRewardFactor(1.0)
                .maxRewardPerSessionCents(20_000L)
                .averageRewardPerSessionCents(15_000L)
                .build();
    }

    private Campaign givenSessionOf(Campaign campaign) {
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));
        when(campaignRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(campaign));
        return campaign;
    }

    private KeyWallet givenKeyWalletAndMultiplier(double multiplier) {
        KeyWallet keyWallet = KeyWallet.builder().purchaseKeysCents(0L).connectivityKeysCents(0L).build();
        when(keyWalletService.getByConsumerIdForUpdate(42L)).thenReturn(keyWallet);
        when(levelService.getMultiplier(42L)).thenReturn(multiplier);
        when(keyWalletService.calculate(anyLong())).thenAnswer(inv -> {
            long total = inv.getArgument(0);
            long purchase = Math.round(total * 0.75);
            return new RewardSplit(purchase, total - purchase);
        });
        when(keyWalletService.calculatePurchaseExpiry()).thenReturn(ZonedDateTime.now().plusMonths(1));
        when(keyWalletService.calculateConnectivityExpiry()).thenReturn(ZonedDateTime.now().plusDays(1));
        return keyWallet;
    }

    @Test
    @DisplayName("cierra la sesión, cobra a la campaña con el puntaje de performance, acredita llaves y pide XP")
    void closesSessionChargesCampaignAndCreditsWithPerformanceScore() {
        Campaign campaign = givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        KeyWallet wallet = givenKeyWalletAndMultiplier(1.0);

        service.submitGameMetrics(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getEndTime()).isNotNull();
        assertThat(session.getScore()).isEqualTo(3);
        // min(tope, completar + 3 × factor)
        assertThat(session.getCoinsEarned()).isEqualTo(5_003L);
        assertThat(campaign.getSpentCents()).isEqualTo(15_003L);
        verify(keyLedgerService).issue(org.mockito.ArgumentMatchers.eq(wallet), any());
        verify(gameSessionRepository).save(session);

        ArgumentCaptor<XpAwardRequestedEvent> captor = ArgumentCaptor.forClass(XpAwardRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getConsumerId()).isEqualTo(42L);
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.GAME_PLAYED);
    }

    @Test
    @DisplayName("sin métrica performance paga solo la recompensa por completar")
    void withoutPerformancePaysCompletionRewardOnly() {
        event = eventWith(new GameMetricDTO("result", MetricType.STRING, "win"));
        Campaign campaign = givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        givenKeyWalletAndMultiplier(1.0);

        service.submitGameMetrics(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getScore()).isNull();
        assertThat(session.getCoinsEarned()).isEqualTo(5_000L);
        assertThat(campaign.getSpentCents()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("performance como texto cuenta como sin puntaje")
    void nonNumericPerformancePaysCompletionRewardOnly() {
        event = eventWith(performance("3"));
        givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        givenKeyWalletAndMultiplier(1.0);

        service.submitGameMetrics(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getScore()).isNull();
        assertThat(session.getCoinsEarned()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("el multiplicador de nivel se aplica a lo acreditado, no a lo cobrado")
    void appliesLevelMultiplierToChargedAmount() {
        givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        givenKeyWalletAndMultiplier(1.5);

        service.submitGameMetrics(event);

        assertThat(session.getCoinsEarned()).isEqualTo(5_003L);
        assertThat(session.getCreditedAmountCents()).isEqualTo(Math.round(5_003L * 1.5));
    }

    @Test
    @DisplayName("sesión ya cerrada: no hace nada, sin excepción")
    void alreadyClosedSessionIsNoOp() {
        session.setCompleted(true);
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        service.submitGameMetrics(event);

        assertNothingPaid(campaign);
    }

    @Test
    @DisplayName("cerrada y ya vencida: no-op (200), no 422")
    void alreadyClosedAndExpiredSessionIsNoOp() {
        session.setCompleted(true);
        session.setStartTime(ZonedDateTime.now().minusMinutes(90));
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        service.submitGameMetrics(event);

        assertNothingPaid(campaign);
    }

    private void assertNothingPaid(Campaign campaign) {
        assertThat(campaign.getSpentCents()).isEqualTo(10_000L);
        verify(campaignRepository, never()).findByIdForUpdate(any());
        verify(campaignRepository, never()).save(any());
        verify(keyLedgerService, never()).issue(any(), any());
        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("segundo envío tras el primero: cobra, acredita y pide XP una sola vez")
    void secondDeliveryAfterLockSeesClosedSessionAndChargesOnce() {
        Campaign campaign = givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        givenKeyWalletAndMultiplier(1.0);

        service.submitGameMetrics(event);
        service.submitGameMetrics(event);

        assertThat(campaign.getSpentCents()).isEqualTo(15_003L);
        verify(campaignRepository, times(1)).findByIdForUpdate(7L);
        verify(keyLedgerService, times(1)).issue(any(), any());
        verify(eventPublisher, times(1)).publishEvent(any(ApplicationEvent.class));
        verify(gameSessionRepository, times(1)).save(session);
    }

    @Test
    @DisplayName("toma la sesión con FOR UPDATE, nunca con la lectura simple")
    void locksSessionForUpdate() {
        session.setCompleted(true);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        service.submitGameMetrics(event);

        verify(gameSessionRepository).findBySessionTokenForUpdate(TOKEN);
        verify(gameSessionRepository, never()).findBySessionToken(anyString());
    }

    @Test
    @DisplayName("submitGameMetrics se reintenta ante conflicto de concurrencia")
    void isRetriedOnConcurrencyConflict() throws NoSuchMethodException {
        assertThat(GameServiceImpl.class.getMethod("submitGameMetrics", GameEventDTO.class)
                .isAnnotationPresent(RetryOnConcurrencyConflict.class)).isTrue();
    }

    @Test
    @DisplayName("hash ajeno: 401 sin cobrar ni publicar")
    void foreignHashIsRejectedWithoutPaying() {
        event.setUserHash("hash-de-otro");
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitGameMetrics(event)).isInstanceOf(UnauthorizedException.class);

        assertThat(session.isCompleted()).isFalse();
        assertNothingPaid(campaign);
    }

    @Test
    @DisplayName("hash ajeno sobre sesión cerrada: 401, no 200")
    void foreignHashOnClosedSessionIsRejected() {
        session.setCompleted(true);
        event.setUserHash("hash-de-otro");
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitGameMetrics(event)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("sesión vencida y abierta: 422 sin cobrar")
    void expiredSessionIsRejectedWithoutPaying() {
        session.setStartTime(ZonedDateTime.now().minusMinutes(60));
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitGameMetrics(event)).isInstanceOf(BusinessException.class);

        assertThat(session.isCompleted()).isFalse();
        assertNothingPaid(campaign);
    }

    @Test
    @DisplayName("sesión inexistente: EntityNotFoundException")
    void unknownSessionIsRejected() {
        when(gameSessionRepository.findBySessionTokenForUpdate(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitGameMetrics(event)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("presupuesto agotado: cierra sin cobro ni llaves y pide XP")
    void exhaustedBudgetClosesWithoutChargeAndRequestsXp() {
        Campaign campaign = givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 100_000L));

        service.submitGameMetrics(event);

        assertClosedWithoutCharge(campaign);
    }

    @Test
    @DisplayName("campaña COMPLETED o CANCELLED: cierra sin cobro ni llaves y pide XP")
    void campaignNotActiveClosesWithoutChargeAndRequestsXp() {
        Campaign completed = givenSessionOf(campaignWith(CampaignStatus.COMPLETED, 50_000L));
        service.submitGameMetrics(event);
        assertClosedWithoutCharge(completed);
    }

    @Test
    @DisplayName("campaña CANCELLED: cierra sin cobro ni llaves y pide XP")
    void cancelledCampaignClosesWithoutChargeAndRequestsXp() {
        Campaign cancelled = givenSessionOf(campaignWith(CampaignStatus.CANCELLED, 50_000L));
        service.submitGameMetrics(event);
        assertClosedWithoutCharge(cancelled);
    }

    private void assertClosedWithoutCharge(Campaign campaign) {
        long spentBefore = campaign.getSpentCents();
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getCoinsEarned()).isZero();
        assertThat(session.isRewardGranted()).isFalse();
        assertThat(campaign.getSpentCents()).isEqualTo(spentBefore);
        verify(campaignRepository, never()).save(any());
        verify(keyLedgerService, never()).issue(any(), any());
        verify(gameSessionRepository).save(session);
        verify(eventPublisher).publishEvent(any(XpAwardRequestedEvent.class));
    }

    @Test
    @DisplayName("cuerpo capturado de Stack Tower: cierra con puntaje 3")
    void stackTowerCaptureClosesWithScore3() throws Exception {
        ObjectMapper mapper = new AuditConfig().auditObjectMapper();
        GameEventDTO<List<GameMetricDTO>> captured;
        try (InputStream in = getClass().getResourceAsStream("/games/stack-tower-metrics.json")) {
            captured = mapper.readValue(in, new TypeReference<GameEventDTO<List<GameMetricDTO>>>() { });
        }
        session.setSessionToken("token-stack-1");
        session.setUserHash("hash-stack-1");
        session.setCampaign(campaignWith(CampaignStatus.ACTIVE, 10_000L));
        when(gameSessionRepository.findBySessionTokenForUpdate("token-stack-1")).thenReturn(Optional.of(session));
        when(campaignRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(session.getCampaign()));
        givenKeyWalletAndMultiplier(1.0);

        service.submitGameMetrics(captured);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getScore()).isEqualTo(3);
    }

    @Test
    @DisplayName("ningún log contiene el session_token ni el user_hash (cierre, no-op y rechazo)")
    void logsNeverContainTokenOrHash() {
        Logger logger = (Logger) LoggerFactory.getLogger(GameServiceImpl.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            // cierre con cobro
            givenSessionOf(campaignWith(CampaignStatus.ACTIVE, 10_000L));
            givenKeyWalletAndMultiplier(1.0);
            service.submitGameMetrics(event);
            // no-op
            service.submitGameMetrics(event);
            // rechazo
            GameEventDTO<List<GameMetricDTO>> foreign = eventWith(performance(3));
            foreign.setUserHash("hash-de-otro");
            assertThatThrownBy(() -> service.submitGameMetrics(foreign)).isInstanceOf(UnauthorizedException.class);

            assertThat(appender.list).isNotEmpty();
            for (ILoggingEvent logged : appender.list) {
                assertThat(logged.getFormattedMessage()).doesNotContain(TOKEN).doesNotContain(HASH);
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }
}
