package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.verygana2.exceptions.financeExceptions.WalletAlreadyExistsException;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.plans.BudgetTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WalletServiceImpl}: alta del wallet (una sola vez por
 * comercial) y el resumen de facturación, que debe comportarse distinto
 * según el comercial tenga plan BASIC (sin wallet, con Subscription) o
 * STANDARD/PREMIUM (con wallet, sin Subscription).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletServiceImpl")
class WalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private CommercialDetailsService commercialDetailsService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private PayoutRepository payoutRepository;
    @Mock private BudgetTransactionRepository budgetTransactionRepository;

    private WalletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(walletRepository, commercialDetailsService, subscriptionRepository,
                investmentRepository, payoutRepository, budgetTransactionRepository);
    }

    private CommercialDetails commercial(Long id, Plan plan) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        c.setCurrentPlan(plan);
        return c;
    }

    @Nested
    @DisplayName("createFor")
    class CreateFor {

        @Test
        @DisplayName("comercial sin wallet: lo crea")
        void withoutExistingWallet_createsIt() {
            when(walletRepository.existsByCommercialId(1L)).thenReturn(false);
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(commercial(1L, null));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

            Wallet wallet = service.createFor(1L);

            assertThat(wallet.getCommercial().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("comercial que ya tiene wallet: lanza WalletAlreadyExistsException")
        void alreadyHasWallet_throwsException() {
            when(walletRepository.existsByCommercialId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.createFor(1L)).isInstanceOf(WalletAlreadyExistsException.class);
        }
    }

    @Nested
    @DisplayName("getBillingSummary")
    class GetBillingSummary {

        @Test
        @DisplayName("plan BASIC con suscripción activa: usa endDate/daysRemaining de la Subscription, no del wallet")
        void basicPlanWithActiveSubscription_usesSubscriptionDates() {
            Plan basic = Plan.builder().code(PlanCode.BASIC).name("Básico").build();
            CommercialDetails commercial = commercial(1L, basic);
            Subscription sub = Subscription.builder().plan(basic)
                    .status(SubscriptionStatus.ACTIVE)
                    .endDate(java.time.ZonedDateTime.now().plusDays(15)).build();

            when(commercialDetailsService.getCommercialById(1L)).thenReturn(commercial);
            when(walletRepository.findByCommercialId(1L)).thenReturn(Optional.empty()); // BASIC no tiene wallet
            when(payoutRepository.sumTotalByCommercialIdAndPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
            when(subscriptionRepository.findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(sub));

            var summary = service.getBillingSummary(1L);

            assertThat(summary.getBalanceCents()).isZero(); // sin wallet
            assertThat(summary.getCurrentPlan().getDaysRemaining()).isBetween(13L, 15L);
            assertThat(summary.getCurrentPlan().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("plan STANDARD/PREMIUM: usa el saldo y status del wallet, sin días restantes")
        void standardPlan_usesWalletBalanceAndStatus() {
            Plan standard = Plan.builder().code(PlanCode.STANDARD).name("Estándar").build();
            CommercialDetails commercial = commercial(1L, standard);
            Wallet wallet = new Wallet();
            wallet.setId(10L);
            wallet.setBalanceCents(750_000L);
            wallet.setStatus(WalletStatus.ACTIVE);
            commercial.setWallet(wallet);

            when(commercialDetailsService.getCommercialById(1L)).thenReturn(commercial);
            when(walletRepository.findByCommercialId(1L)).thenReturn(Optional.of(wallet));
            when(budgetTransactionRepository.sumByWalletIdAndPeriod(any(), any(), any())).thenReturn(BigDecimal.valueOf(50_000));
            when(payoutRepository.sumTotalByCommercialIdAndPeriod(any(), any(), any())).thenReturn(BigDecimal.valueOf(200_000));

            var summary = service.getBillingSummary(1L);

            assertThat(summary.getBalanceCents()).isEqualTo(750_000L);
            assertThat(summary.getSpentThisMonthCents()).isEqualTo(50_000L);
            assertThat(summary.getEarnedThisMonthCents()).isEqualTo(200_000L);
            assertThat(summary.getCurrentPlan().getDaysRemaining()).isNull();
            assertThat(summary.getCurrentPlan().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("sin plan asignado: currentPlan queda null en el resumen")
        void noCurrentPlan_currentPlanIsNull() {
            CommercialDetails commercial = commercial(1L, null);

            when(commercialDetailsService.getCommercialById(1L)).thenReturn(commercial);
            when(walletRepository.findByCommercialId(1L)).thenReturn(Optional.empty());
            when(payoutRepository.sumTotalByCommercialIdAndPeriod(any(), any(), any())).thenReturn(null);

            var summary = service.getBillingSummary(1L);

            assertThat(summary.getCurrentPlan()).isNull();
            assertThat(summary.getEarnedThisMonthCents()).isZero(); // null del repo se convierte en 0
        }
    }

    @Test
    @DisplayName("getPayouts: delega en el repositorio mapeando cada Payout a su DTO resumen")
    void getPayouts_mapsPayoutsToSummaryDTO() {
        var pageable = PageRequest.of(0, 10);
        when(payoutRepository.findByCommercialIdAndPeriod(org.mockito.ArgumentMatchers.eq(1L), any(), any(), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        var result = service.getPayouts(1L, 2026, 3, pageable);

        assertThat(result.getData()).isEmpty();
    }
}
