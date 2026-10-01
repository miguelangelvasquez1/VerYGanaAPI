package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.EndSessionResponseDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameMetricDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.repositories.games.GameSessionMetricRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.games.GameServiceImpl;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.utils.validators.MetricValidator;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl.completeSession")
class GameServiceCompleteSessionTest {

    @Mock ObjectMapper objectMapper;
    @Mock GameRepository gameRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock BrandingRequestRepository brandingRequestRepository;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameMetricDefinitionRepository metricDefinitionRepository;
    @Mock MetricValidator metricValidator;
    @Mock GameSessionMetricRepository gameSessionMetricRepository;
    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock KeyWalletService keyWalletService;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock LevelService levelService;

    @InjectMocks GameServiceImpl service;

    private GameSession session;
    private GameEventDTO<EndSessionDTO> event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sessionExpirationTime", 30);
        ReflectionTestUtils.setField(service, "keyValueCents", 1_000L);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(42L);

        session = new GameSession();
        session.setSessionToken("token-1");
        session.setUserHash("hash-1");
        session.setConsumer(consumer);
        session.setStartTime(ZonedDateTime.now().minusMinutes(2));
        session.setCompleted(false);

        EndSessionDTO payload = new EndSessionDTO();
        payload.setFinalScore(1500);

        event = new GameEventDTO<>();
        event.setSessionToken("token-1");
        event.setUserHash("hash-1");
        event.setPayload(payload);
    }

    @Test
    @DisplayName("marca la sesión completada y publica XP GAME_PLAYED del consumer dueño; sin campaña no acredita llaves")
    void completesSessionAndPublishesGamePlayedXp() {
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));

        service.completeSession(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getEndTime()).isNotNull();
        verify(gameSessionRepository).save(session);

        ArgumentCaptor<XpAwardRequestedEvent> captor =
                ArgumentCaptor.forClass(XpAwardRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getConsumerId()).isEqualTo(42L);
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.GAME_PLAYED);

        // Sin campaña no hay cobro, así que tampoco debe acreditarse ninguna llave al jugador,
        // ni la sesión entra a la liquidación de tesorería.
        assertThat(session.getCreditedAmountCents()).isNull();
        verify(keyWalletService, never()).getByConsumerId(any());
        verify(keyWalletRepository, never()).save(any());
        verify(keyTransactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("sesión ya completada: rechaza el reintento y NO duplica el XP")
    void alreadyCompletedSessionRejectsRetryWithoutDuplicateXp() {
        session.setCompleted(true);
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("sesión expirada: no publica XP")
    void expiredSessionDoesNotPublishXp() {
        session.setStartTime(ZonedDateTime.now().minusMinutes(60));
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    @DisplayName("hash ajeno: no publica XP")
    void foreignHashDoesNotPublishXp() {
        event.setUserHash("hash-de-otro");
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event))
                .isInstanceOf(RuntimeException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    // ─── Cobro de la sesión al presupuesto de la campaña ────────────────────

    /** Campaña con 100.000 ¢ de presupuesto y las recompensas de los seeds (completar 5.000, factor 1, tope 20.000). */
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

    private void givenSessionOf(Campaign campaign) {
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));
        when(campaignRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(campaign));
    }

    /**
     * Deja lista la acreditación de llaves para el consumer 42L: un KeyWallet real (para poder
     * comprobar el saldo final) y {@code calculate} con el mismo split 75/25 (redondeado al
     * centavo, igual que {@code KeyWalletServiceImpl.calculate}) que usa producción.
     */
    private KeyWallet givenKeyWalletAndMultiplier(double multiplier) {
        KeyWallet keyWallet = KeyWallet.builder().purchaseKeysCents(0L).connectivityKeysCents(0L).build();
        when(keyWalletService.getByConsumerId(42L)).thenReturn(keyWallet);
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
    @DisplayName("cobra el costo a la campaña, lo deja en coinsEarned y acredita esas llaves al jugador")
    void chargesCampaignAndCreditsPlayer() {
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        givenSessionOf(campaign);
        KeyWallet keyWallet = givenKeyWalletAndMultiplier(1.0);

        service.completeSession(event);   // finalScore 1.500 → 5.000 + 1.500 = 6.500 ¢

        assertThat(campaign.getSpentCents()).isEqualTo(16_500L);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(session.getCoinsEarned()).isEqualTo(6_500L);
        assertThat(session.isRewardGranted()).isTrue();
        verify(campaignRepository).save(campaign);
        verify(gameSessionRepository).save(session);

        // 6.500 ¢ × multiplicador 1.0 → split 75/25 = 4.875 / 1.625
        assertThat(keyWallet.getPurchaseKeysCents()).isEqualTo(4_875L);
        assertThat(keyWallet.getConnectivityKeysCents()).isEqualTo(1_625L);
        verify(keyWalletRepository).save(keyWallet);
        verify(keyTransactionRepository).saveAll(any());
    }

    @Test
    @DisplayName("el multiplicador de nivel del jugador se aplica antes de repartir en llaves")
    void appliesLevelMultiplierBeforeSplitting() {
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        givenSessionOf(campaign);
        KeyWallet keyWallet = givenKeyWalletAndMultiplier(2.0);

        EndSessionResponseDTO response = service.completeSession(event);   // cobrado 6.500 ¢ × 2.0 = 13.000 ¢ ajustados

        // 13.000 ¢ → split 75/25 = 9.750 / 3.250
        assertThat(keyWallet.getPurchaseKeysCents()).isEqualTo(9_750L);
        assertThat(keyWallet.getConnectivityKeysCents()).isEqualTo(3_250L);

        // El juego ve las llaves ya multiplicadas: 13.000 ¢ / 1.000 ¢ por llave.
        assertThat(response.rewardGranted()).isTrue();
        assertThat(response.keysEarned()).isEqualTo(13L);
    }

    @Test
    @DisplayName("deja en la sesión lo financiado y lo acreditado, sin liquidar: el diferencial lo cuadra el job de tesorería")
    void recordsFundedAndCreditedForSettlement() {
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        givenSessionOf(campaign);
        givenKeyWalletAndMultiplier(2.0);

        service.completeSession(event);

        // Sin creditedAmountCents, KeyIssuanceSettlementService no ve la sesión y los 6.500 ¢
        // de exceso que emitió el multiplicador quedan sin respaldo en KEYS_RESERVE.
        assertThat(session.getCoinsEarned()).isEqualTo(6_500L);
        assertThat(session.getCreditedAmountCents()).isEqualTo(13_000L);
        assertThat(session.isIssuanceSettled()).isFalse();
    }

    @Test
    @DisplayName("sin payload la partida cierra igual y cobra solo la recompensa por completar")
    void missingPayloadClosesWithCompletionRewardOnly() {
        event.setPayload(null);
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        givenSessionOf(campaign);
        givenKeyWalletAndMultiplier(1.0);

        EndSessionResponseDTO response = service.completeSession(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getScore()).isNull();
        assertThat(session.getCoinsEarned()).isEqualTo(5_000L);
        assertThat(response.keysEarned()).isEqualTo(5L);
    }

    @Test
    @DisplayName("la sesión que agota el presupuesto se recorta a lo que queda, la campaña pasa a COMPLETED y se acredita solo lo cobrado")
    void lastSessionCompletesTheCampaignAndCreditsClippedAmount() {
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 98_000L);   // quedan 2.000 ¢
        givenSessionOf(campaign);
        KeyWallet keyWallet = givenKeyWalletAndMultiplier(1.0);

        service.completeSession(event);

        assertThat(campaign.getSpentCents()).isEqualTo(100_000L);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(session.getCoinsEarned()).isEqualTo(2_000L);
        assertThat(session.isRewardGranted()).isTrue();

        // Se acredita lo recortado (2.000 ¢), no lo que la sesión hubiera costado sin recortar.
        assertThat(keyWallet.getPurchaseKeysCents()).isEqualTo(1_500L);
        assertThat(keyWallet.getConnectivityKeysCents()).isEqualTo(500L);
    }

    @Test
    @DisplayName("campaña ya COMPLETED: la sesión cierra igual y publica XP, sin cobro ni crédito al jugador")
    void completedCampaign_sessionClosesWithoutChargeOrCredit() {
        Campaign campaign = campaignWith(CampaignStatus.COMPLETED, 100_000L);
        givenSessionOf(campaign);

        service.completeSession(event);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getCoinsEarned()).isZero();
        assertThat(session.isRewardGranted()).isFalse();
        assertThat(campaign.getSpentCents()).isEqualTo(100_000L);
        verify(campaignRepository, never()).save(any());
        verify(gameSessionRepository).save(session);
        verify(eventPublisher).publishEvent(any(XpAwardRequestedEvent.class));

        // Sin cobro no hay crédito.
        verify(keyWalletService, never()).getByConsumerId(any());
        verify(keyWalletRepository, never()).save(any());
        verify(keyTransactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("sesión ya completada: no vuelve a cobrar a la campaña ni a acreditar llaves (cierre duplicado)")
    void alreadyCompletedSession_doesNotChargeOrCreditAgain() {
        session.setCompleted(true);
        Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 10_000L);
        session.setCampaign(campaign);
        when(gameSessionRepository.findBySessionTokenForUpdate("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event)).isInstanceOf(BusinessException.class);

        assertThat(campaign.getSpentCents()).isEqualTo(10_000L);
        verify(campaignRepository, never()).findByIdForUpdate(any());
        verify(campaignRepository, never()).save(any());
        verify(keyWalletService, never()).getByConsumerId(any());
        verify(keyWalletRepository, never()).save(any());
        verify(keyTransactionRepository, never()).saveAll(any());
    }
}