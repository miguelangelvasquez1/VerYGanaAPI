package com.verygana2.services.ads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.dtos.BudgetIncreaseResponseDTO;
import com.verygana2.dtos.ad.requests.IncreaseAdBudgetRequestDTO;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.StaleBudgetException;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.PricingConfigService;
import com.verygana2.services.plans.PlanFeatureGuard;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdServiceImpl — aumento de presupuesto")
class AdServiceImplBudgetIncreaseTest {

    private static final Long AD_ID = 501L;
    private static final Long COMMERCIAL_ID = 7L;
    private static final long REWARD_PER_LIKE = 100L;
    /** 4 s facturables × 25 ¢/s = 100 ¢: el anuncio de prueba está justo en el mínimo vigente. */
    private static final int BILLABLE_SECONDS = 4;
    private static final long COST_PER_SECOND_CENTS = 25L;
    private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");

    @Mock AdRepository adRepository;
    @Mock WalletRepository walletRepository;
    @Mock PlanFeatureGuard planFeatureGuard;
    @Mock PricingConfigService pricingConfigService;
    @Mock Clock clock;

    @InjectMocks AdServiceImpl adServiceImpl;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        when(pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS))
                .thenReturn(COST_PER_SECOND_CENTS);

        wallet = new Wallet();
        wallet.setBalanceCents(1_000_000L);
        when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adRepository.save(any(Ad.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Un anuncio de 10 likes a 100 ¢ c/u; {@code currentLikes} likes ya consumidos. */
    private Ad adWith(AdStatus status, int currentLikes) {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);

        AdAsset asset = new AdAsset();
        asset.setDurationSeconds(BILLABLE_SECONDS);

        return Ad.builder()
                .id(AD_ID)
                .status(status)
                .rewardPerLike(REWARD_PER_LIKE)
                .maxLikes(10)
                .currentLikes(currentLikes)
                .commercial(commercial)
                .asset(asset)
                .build();
    }

    private void givenAd(Ad ad) {
        when(adRepository.findByIdAndCommercialIdForUpdate(AD_ID, COMMERCIAL_ID)).thenReturn(Optional.of(ad));
    }

    /** Aumento con la capacidad que el cliente ve hoy (10 likes, el default de {@link #adWith}). */
    private BudgetIncreaseResponseDTO increase(int additionalLikes) {
        return increase(10, additionalLikes);
    }

    private BudgetIncreaseResponseDTO increase(int expectedMaxLikes, int additionalLikes) {
        return adServiceImpl.increaseAdBudget(AD_ID,
                IncreaseAdBudgetRequestDTO.builder()
                        .expectedMaxLikes(expectedMaxLikes)
                        .additionalLikes(additionalLikes)
                        .build(),
                COMMERCIAL_ID);
    }

    @Nested
    @DisplayName("estados que admiten aumento")
    class AllowedStatuses {

        @Test
        @DisplayName("ACTIVE: cobra rewardPerLike × likes, sube maxLikes y conserva el estado")
        void active_chargesAndKeepsStatus() {
            Ad ad = adWith(AdStatus.ACTIVE, 4);
            givenAd(ad);

            BudgetIncreaseResponseDTO result = increase(5);

            assertThat(ad.getMaxLikes()).isEqualTo(15);
            assertThat(ad.getStatus()).isEqualTo(AdStatus.ACTIVE);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L - 500L);
            verify(adRepository).save(ad);
            verify(planFeatureGuard, never()).assertCanReopen(any(), any());

            assertThat(result.getChargedCents()).isEqualTo(500L);
            assertThat(result.getTotalBudgetCents()).isEqualTo(1_500L);      // 15 × 100
            assertThat(result.getRemainingBudgetCents()).isEqualTo(1_100L);  // (15 − 4) × 100
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.isReopened()).isFalse();
            assertThat(result.getWalletBalanceCents()).isEqualTo(999_500L);
        }

        @Test
        @DisplayName("PAUSED: sube el presupuesto pero sigue PAUSED (no se activa solo)")
        void paused_staysPaused() {
            Ad ad = adWith(AdStatus.PAUSED, 2);
            givenAd(ad);

            BudgetIncreaseResponseDTO result = increase(3);

            assertThat(ad.getMaxLikes()).isEqualTo(13);
            assertThat(ad.getStatus()).isEqualTo(AdStatus.PAUSED);
            assertThat(result.isReopened()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED: se reabre a ACTIVE, limpia endDate y exige cupo MAX_ADS")
        void completed_reopens() {
            Ad ad = adWith(AdStatus.COMPLETED, 10);
            ad.setEndDate(ZonedDateTime.parse("2025-12-31T00:00:00Z"));
            givenAd(ad);

            BudgetIncreaseResponseDTO result = increase(5);

            verify(planFeatureGuard).assertCanReopen(COMMERCIAL_ID, Capability.MAX_ADS);
            assertThat(ad.getStatus()).isEqualTo(AdStatus.ACTIVE);
            assertThat(ad.getEndDate()).isNull();
            assertThat(ad.getMaxLikes()).isEqualTo(15);
            assertThat(ad.canReceiveLike()).isTrue();

            assertThat(result.isReopened()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getRemainingBudgetCents()).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("rechazos")
    class Rejections {

        @ParameterizedTest
        @EnumSource(value = AdStatus.class, names = {"PENDING", "APPROVED", "REJECTED", "BLOCKED"})
        @DisplayName("estados fuera del ciclo activo: InvalidAdStateException y sin tocar la wallet")
        void disallowedStatus_rejected(AdStatus status) {
            Ad ad = adWith(status, 0);
            givenAd(ad);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(InvalidAdStateException.class);

            assertThat(ad.getMaxLikes()).isEqualTo(10);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(adRepository, never()).save(any());
        }

        @Test
        @DisplayName("anuncio inexistente o de otro comercial: AdNotFoundException")
        void notOwned_notFound() {
            when(adRepository.findByIdAndCommercialIdForUpdate(AD_ID, COMMERCIAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> increase(5)).isInstanceOf(AdNotFoundException.class);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("superar 10.000.000 likes: ValidationException y sin cobrar")
        void exceedsMaxLikes_rejected() {
            Ad ad = adWith(AdStatus.ACTIVE, 0);
            ad.setMaxLikes(9_999_999);
            givenAd(ad);

            assertThatThrownBy(() -> increase(9_999_999, 2)).isInstanceOf(ValidationException.class);

            assertThat(ad.getMaxLikes()).isEqualTo(9_999_999);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente: InsufficientFundsException y el anuncio queda intacto")
        void insufficientFunds_adUntouched() {
            wallet.setBalanceCents(499L);
            Ad ad = adWith(AdStatus.COMPLETED, 10);
            givenAd(ad);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(InsufficientFundsException.class);

            assertThat(ad.getMaxLikes()).isEqualTo(10);
            assertThat(ad.getStatus()).isEqualTo(AdStatus.COMPLETED);
            verify(adRepository, never()).save(any());
        }

        @Test
        @DisplayName("COMPLETED sin cupo en el plan: PlanCapabilityException y sin cobrar")
        void completedWithoutSlot_rejectedBeforeCharging() {
            Ad ad = adWith(AdStatus.COMPLETED, 10);
            givenAd(ad);
            doThrow(new PlanCapabilityException("Límite de anuncios alcanzado"))
                    .when(planFeatureGuard).assertCanReopen(COMMERCIAL_ID, Capability.MAX_ADS);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(PlanCapabilityException.class);

            assertThat(ad.getStatus()).isEqualTo(AdStatus.COMPLETED);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(adRepository, never()).save(any());
        }

        @Test
        @DisplayName("capacidad esperada distinta de la actual: StaleBudgetException (409) y sin cobrar")
        void staleExpectedMaxLikes_rejectedWithoutCharging() {
            Ad ad = adWith(AdStatus.ACTIVE, 0);
            givenAd(ad);

            assertThatThrownBy(() -> increase(7, 5)).isInstanceOf(StaleBudgetException.class);

            assertThat(ad.getMaxLikes()).isEqualTo(10);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(adRepository, never()).save(any());
        }

        @Test
        @DisplayName("doble envío idéntico (doble clic / reintento): cobra una sola vez, el segundo es 409")
        void doubleSubmit_chargedOnce() {
            Ad ad = adWith(AdStatus.ACTIVE, 0);
            givenAd(ad);

            increase(10, 5);                                                  // primer clic: aplica
            assertThatThrownBy(() -> increase(10, 5))                         // segundo clic: mismo "esperado"
                    .isInstanceOf(StaleBudgetException.class);

            assertThat(ad.getMaxLikes()).isEqualTo(15);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L - 500L); // una sola vez
        }

        @Test
        @DisplayName("precio por like por debajo del mínimo vigente: ValidationException y sin cobrar")
        void belowCurrentMinimumPrice_rejected() {
            // El costo por segundo subió a 30 ¢ → mínimo 4 × 30 = 120 → 120 ¢ > 100 ¢ del anuncio.
            when(pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS))
                    .thenReturn(30L);
            Ad ad = adWith(AdStatus.COMPLETED, 10);
            givenAd(ad);

            assertThatThrownBy(() -> increase(5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("100")
                    .hasMessageContaining("120");

            assertThat(ad.getStatus()).isEqualTo(AdStatus.COMPLETED);
            verify(planFeatureGuard, never()).assertCanReopen(any(), any());
            verify(walletRepository, never()).save(any());
            verify(adRepository, never()).save(any());
        }

        @Test
        @DisplayName("el mínimo se redondea a múltiplo de 10 hacia arriba, igual que al crear")
        void minimumIsRoundedUpToMultipleOf10() {
            // 4 s × 26 ¢ = 104 → sube a 110 > 100 ¢ del anuncio.
            when(pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS))
                    .thenReturn(26L);
            givenAd(adWith(AdStatus.ACTIVE, 0));

            assertThatThrownBy(() -> increase(5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("110");
        }

        @Test
        @DisplayName("anuncio sin asset analizado: InvalidAdStateException (no se puede validar el mínimo)")
        void missingAsset_rejected() {
            Ad ad = adWith(AdStatus.ACTIVE, 0);
            ad.setAsset(null);
            givenAd(ad);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(InvalidAdStateException.class);
            verify(walletRepository, never()).save(any());
        }
    }
}
