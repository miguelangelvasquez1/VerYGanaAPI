package com.verygana2.services.ads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.storage.service.R2Service;

/**
 * Verifica que el multiplicador de nivel se aplique correctamente a las LLAVES
 * ganadas al dar like a un anuncio (AdLikeServiceImpl.processAdLike).
 *
 * El multiplicador (LevelService.getMultiplier) se prueba aparte; aquí se cubre
 * el cableado: base × multiplicador → redondeo → split → crédito en la wallet,
 * el mismo escenario que valida a mano el anuncio de prueba del seed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdLikeServiceImpl — multiplicador de nivel sobre las llaves")
class AdLikeServiceImplTest {

    private static final Long CONSUMER_ID = 42L;
    private static final Long AD_ID = 900L;
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");
    private static final ZonedDateTime NOW = ZonedDateTime.ofInstant(FIXED, ZoneOffset.UTC);

    @Mock AdLikeRepository adLikeRepository;
    @Mock ConsumerDetailsService consumerDetailsService;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock KeyWalletService keyWalletService;
    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock AdRepository adRepository;
    @Mock AdService adService;
    @Mock AdWatchSessionRepository adWatchSessionRepository;
    @Mock ConsumerDetailsRepository consumerDetailsRepository;
    @Mock AdScoringConfig adScoringConfig;
    @Mock Clock clock;
    @Mock AdScorer adScorer;
    @Mock AdMapper adMapper;
    @Mock R2Service r2Service;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock LevelService levelService;

    @InjectMocks AdLikeServiceImpl service;

    private KeyWallet wallet;
    private ConsumerDetails consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "keyValueCents", 1000L);

        wallet = new KeyWallet();
        consumer = new ConsumerDetails();
        consumer.setId(CONSUMER_ID);
        consumer.setKeyWallet(wallet);

        // Reloj fijo: la sesión se ve "recién vista" y no expirada.
        when(clock.instant()).thenReturn(FIXED);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /** Anuncio ACTIVE, elegible para like, con la recompensa base indicada. */
    private Ad ad(long rewardPerLikeCents) {
        AdAsset asset = AdAsset.builder().durationSeconds(0).build();
        return Ad.builder()
                .id(AD_ID)
                .status(AdStatus.ACTIVE)
                .rewardPerLike(rewardPerLikeCents)
                .maxLikes(100)
                .currentLikes(0)
                .maxLikesPerUserPerDay(null)
                .asset(asset)
                .build();
    }

    /** Sesión de visualización ACTIVE, dentro de la ventana de tiempo. */
    private AdWatchSession session(Ad ad) {
        AdWatchSession s = new AdWatchSession(consumer, ad);
        s.setStatus(AdWatchSessionStatus.ACTIVE);
        s.setStartedAt(NOW);
        s.setExpiresAt(NOW.plusHours(1));
        return s;
    }

    /** Prepara el camino feliz de processAdLike hasta el cálculo de la recompensa. */
    private void stubHappyPath(Ad ad) {
        when(consumerDetailsService.getConsumerById(CONSUMER_ID)).thenReturn(consumer);
        when(adService.getAdEntityById(AD_ID)).thenReturn(ad);
        when(adWatchSessionRepository.findByIdAndConsumerIdAndAdId(SESSION_ID, CONSUMER_ID, AD_ID))
                .thenReturn(Optional.of(session(ad)));
        when(adLikeRepository.hasUserSeenAd(CONSUMER_ID, AD_ID)).thenReturn(false);
        when(keyWalletService.calculatePurchaseExpiry()).thenReturn(NOW);
        when(keyWalletService.calculateConnectivityExpiry()).thenReturn(NOW);
    }

    @Nested
    @DisplayName("processAdLike aplica el multiplicador a las llaves acreditadas")
    class AppliesMultiplier {

        @Test
        @DisplayName("ORO (×0.7): 10000 base → 7000 llaves acreditadas (7 llaves)")
        void oroMultiplier() {
            Ad ad = ad(10000);
            stubHappyPath(ad);
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(7000L)).thenReturn(new RewardSplit(5250, 1750));

            AdLikedResponse response = service.processAdLike(SESSION_ID, AD_ID, CONSUMER_ID, "127.0.0.1");

            // El split se calcula sobre el monto YA multiplicado.
            verify(keyWalletService).calculate(7000L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(7000L);
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(5250L);
            assertThat(wallet.getConnectivityKeysCents()).isEqualTo(1750L);
            assertThat(response.isLiked()).isTrue();
            assertThat(response.getRewardAmount()).isEqualTo(7L); // 7000 / keyValueCents
        }

        @Test
        @DisplayName("DIAMANTE (×1.0): sin reducción, base = llaves")
        void diamanteKeepsFullValue() {
            Ad ad = ad(10000);
            stubHappyPath(ad);
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(1.0);
            when(keyWalletService.calculate(10000L)).thenReturn(new RewardSplit(7500, 2500));

            AdLikedResponse response = service.processAdLike(SESSION_ID, AD_ID, CONSUMER_ID, "127.0.0.1");

            verify(keyWalletService).calculate(10000L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(10000L);
            assertThat(response.getRewardAmount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("redondea en vez de truncar (5 × 0.7 = 3.5 → 4)")
        void roundsInsteadOfTruncating() {
            Ad ad = ad(5);
            stubHappyPath(ad);
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(4L)).thenReturn(new RewardSplit(3, 1));

            service.processAdLike(SESSION_ID, AD_ID, CONSUMER_ID, "127.0.0.1");

            verify(keyWalletService).calculate(4L); // 3.5 → 4, no 3
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(4L);
        }

        @Test
        @DisplayName("con beneficios pausados gana como BRONCE (×0.5)")
        void pausedEarnsAsBronce() {
            Ad ad = ad(10000);
            stubHappyPath(ad);
            // LevelService reporta el multiplicador de BRONCE cuando está pausado.
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.5);
            when(keyWalletService.calculate(5000L)).thenReturn(new RewardSplit(3750, 1250));

            AdLikedResponse response = service.processAdLike(SESSION_ID, AD_ID, CONSUMER_ID, "127.0.0.1");

            verify(keyWalletService).calculate(5000L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(5000L);
            assertThat(response.getRewardAmount()).isEqualTo(5L);
        }
    }
}
