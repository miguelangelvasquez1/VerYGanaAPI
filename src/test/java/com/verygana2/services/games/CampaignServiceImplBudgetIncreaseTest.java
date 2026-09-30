package com.verygana2.services.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.verygana2.dtos.game.campaign.IncreaseCampaignBudgetRequestDTO;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.StaleBudgetException;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.services.plans.PlanFeatureGuard;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CampaignServiceImpl — aumento de presupuesto")
class CampaignServiceImplBudgetIncreaseTest {

    private static final Long CAMPAIGN_ID = 3L;
    private static final Long USER_ID = 7L;

    @Mock CampaignRepository campaignRepository;
    @Mock WalletRepository walletRepository;
    @Mock PlanFeatureGuard planFeatureGuard;

    @InjectMocks CampaignServiceImpl campaignService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
        wallet.setBalanceCents(1_000_000L);
        when(walletRepository.findByCommercialIdForUpdate(USER_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Campaña con 10.000 ¢ de presupuesto y {@code spentCents} ya gastados. */
    private Campaign campaignWith(CampaignStatus status, long spentCents) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .status(status)
                .budgetCents(10_000L)
                .spentCents(spentCents)
                .build();
    }

    private void givenCampaign(Campaign campaign) {
        when(campaignRepository.findByIdAndCommercialIdForUpdate(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.of(campaign));
    }

    /** Aumento con el presupuesto que el cliente ve hoy (10.000 c, el default de {@link #campaignWith}). */
    private BudgetIncreaseResponseDTO increase(long additionalBudgetCents) {
        return increase(10_000L, additionalBudgetCents);
    }

    private BudgetIncreaseResponseDTO increase(long expectedBudgetCents, long additionalBudgetCents) {
        return campaignService.increaseCampaignBudget(CAMPAIGN_ID, USER_ID,
                IncreaseCampaignBudgetRequestDTO.builder()
                        .expectedBudgetCents(expectedBudgetCents)
                        .additionalBudgetCents(additionalBudgetCents)
                        .build());
    }

    @Nested
    @DisplayName("estados que admiten aumento")
    class AllowedStatuses {

        @Test
        @DisplayName("ACTIVE: suma al presupuesto, cobra de la wallet y conserva el estado")
        void active_chargesAndKeepsStatus() {
            Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 4_000L);
            givenCampaign(campaign);

            BudgetIncreaseResponseDTO result = increase(5_000L);

            assertThat(campaign.getBudgetCents()).isEqualTo(15_000L);
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
            assertThat(wallet.getBalanceCents()).isEqualTo(995_000L);
            verify(campaignRepository).save(campaign);
            verify(planFeatureGuard, never()).assertCanReopen(any(), any());

            assertThat(result.getChargedCents()).isEqualTo(5_000L);
            assertThat(result.getTotalBudgetCents()).isEqualTo(15_000L);
            assertThat(result.getRemainingBudgetCents()).isEqualTo(11_000L);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.isReopened()).isFalse();
            assertThat(result.getWalletBalanceCents()).isEqualTo(995_000L);
        }

        @Test
        @DisplayName("PAUSED: sube el presupuesto pero sigue PAUSED")
        void paused_staysPaused() {
            Campaign campaign = campaignWith(CampaignStatus.PAUSED, 0L);
            givenCampaign(campaign);

            BudgetIncreaseResponseDTO result = increase(1_000L);

            assertThat(campaign.getBudgetCents()).isEqualTo(11_000L);
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);
            assertThat(result.isReopened()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED: se reabre a ACTIVE y exige cupo MAX_BRANDED_GAMES")
        void completed_reopens() {
            Campaign campaign = campaignWith(CampaignStatus.COMPLETED, 10_000L);
            givenCampaign(campaign);

            BudgetIncreaseResponseDTO result = increase(5_000L);

            verify(planFeatureGuard).assertCanReopen(USER_ID, Capability.MAX_BRANDED_GAMES);
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
            assertThat(campaign.getBudgetCents()).isEqualTo(15_000L);

            assertThat(result.isReopened()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getRemainingBudgetCents()).isEqualTo(5_000L);
        }
    }

    @Nested
    @DisplayName("rechazos")
    class Rejections {

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"DRAFT", "CANCELLED"})
        @DisplayName("estados fuera del ciclo activo: ValidationException y sin tocar la wallet")
        void disallowedStatus_rejected(CampaignStatus status) {
            Campaign campaign = campaignWith(status, 0L);
            givenCampaign(campaign);

            assertThatThrownBy(() -> increase(5_000L)).isInstanceOf(ValidationException.class);

            assertThat(campaign.getBudgetCents()).isEqualTo(10_000L);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(campaignRepository, never()).save(any());
        }

        @Test
        @DisplayName("campaña inexistente o de otro comercial: EntityNotFoundException")
        void notOwned_notFound() {
            when(campaignRepository.findByIdAndCommercialIdForUpdate(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> increase(5_000L)).isInstanceOf(EntityNotFoundException.class);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente: InsufficientFundsException y la campaña queda intacta")
        void insufficientFunds_campaignUntouched() {
            wallet.setBalanceCents(4_999L);
            Campaign campaign = campaignWith(CampaignStatus.COMPLETED, 10_000L);
            givenCampaign(campaign);

            assertThatThrownBy(() -> increase(5_000L)).isInstanceOf(InsufficientFundsException.class);

            assertThat(campaign.getBudgetCents()).isEqualTo(10_000L);
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
            verify(campaignRepository, never()).save(any());
        }

        @Test
        @DisplayName("COMPLETED sin cupo en el plan: PlanCapabilityException y sin cobrar")
        void completedWithoutSlot_rejectedBeforeCharging() {
            Campaign campaign = campaignWith(CampaignStatus.COMPLETED, 10_000L);
            givenCampaign(campaign);
            doThrow(new PlanCapabilityException("Límite de juegos branded alcanzado"))
                    .when(planFeatureGuard).assertCanReopen(USER_ID, Capability.MAX_BRANDED_GAMES);

            assertThatThrownBy(() -> increase(5_000L)).isInstanceOf(PlanCapabilityException.class);

            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(campaignRepository, never()).save(any());
        }

        @Test
        @DisplayName("presupuesto esperado distinto del actual: StaleBudgetException (409) y sin cobrar")
        void staleExpectedBudget_rejectedWithoutCharging() {
            Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 0L);
            givenCampaign(campaign);

            assertThatThrownBy(() -> increase(7_000L, 5_000L)).isInstanceOf(StaleBudgetException.class);

            assertThat(campaign.getBudgetCents()).isEqualTo(10_000L);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(campaignRepository, never()).save(any());
        }

        @Test
        @DisplayName("doble envio identico (doble clic / reintento): cobra una sola vez, el segundo es 409")
        void doubleSubmit_chargedOnce() {
            Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 0L);
            givenCampaign(campaign);

            increase(10_000L, 5_000L);                                        // primer clic: aplica
            assertThatThrownBy(() -> increase(10_000L, 5_000L))               // segundo clic: mismo "esperado"
                    .isInstanceOf(StaleBudgetException.class);

            assertThat(campaign.getBudgetCents()).isEqualTo(15_000L);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L - 5_000L);  // una sola vez
        }

        @Test
        @DisplayName("las sesiones que gastan presupuesto no invalidan el esperado: spentCents no es budgetCents")
        void spendingDoesNotStaleTheExpectedBudget() {
            Campaign campaign = campaignWith(CampaignStatus.ACTIVE, 6_000L);
            givenCampaign(campaign);

            BudgetIncreaseResponseDTO result = increase(10_000L, 5_000L);

            assertThat(result.getTotalBudgetCents()).isEqualTo(15_000L);
            assertThat(result.getRemainingBudgetCents()).isEqualTo(9_000L);
        }
    }
}
