package com.verygana2.services.finance;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.finance.WalletService;
import com.verygana2.services.wompi.WompiService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanServiceImpl}: iniciar el pago de un plan (BASIC vs.
 * STANDARD/PREMIUM tienen validaciones y entidades distintas) y procesar el
 * resultado del webhook de Wompi (activación de suscripción o inversión).
 * Una recarga de inversión nunca cambia el plan del comercial — el cambio de
 * plan es siempre una acción explícita.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanServiceImpl")
class PlanServiceImplTest {

    @Mock private WompiService wompiService;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private TreasuryConfig treasuryConfig;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private PlanRepository planRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;
    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private com.verygana2.services.plans.InvestmentService investmentService;
    @Mock private com.verygana2.services.plans.EffectivePlanResolver effectivePlanResolver;
    @Mock private com.verygana2.services.interfaces.EmailService emailService;
    @Mock private com.verygana2.services.interfaces.commercial.CommercialContractService commercialContractService;
    @Mock private com.verygana2.repositories.commercial.CommercialContractRepository commercialContractRepository;
    @Mock private com.verygana2.repositories.commercial.PlanChangeRequestRepository planChangeRequestRepository;
    @Mock private com.verygana2.services.interfaces.finance.PlanChangeRequestService planChangeRequestService;
    @Mock private com.verygana2.mappers.CommercialOnboardingMapper commercialOnboardingMapper;

    private PlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanServiceImpl(wompiService, wompiTransactionRepository, commercialDetailsRepository,
                treasuryService, treasuryConfig, subscriptionRepository, investmentRepository, planRepository,
                walletRepository, walletService, onboardingRepository, investmentService, effectivePlanResolver,
                emailService, commercialContractService, commercialContractRepository, planChangeRequestRepository,
                planChangeRequestService, commercialOnboardingMapper);
    }

    private CommercialDetails commercial(Long id) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        User user = new User();
        user.setId(id);
        user.setEmail("comercial@test.com");
        user.setPublicId(UUID.randomUUID());
        c.setUser(user);
        return c;
    }

    @Nested
    @DisplayName("initiatePlanPayment — BASIC")
    class InitiateBasic {

        @Test
        @DisplayName("sin suscripción activa vigente: crea Subscription PENDING_PAYMENT y genera checkout")
        void withoutActiveSubscription_createsSubscriptionAndCheckout() {
            CommercialDetails commercial = commercial(1L);
            Plan basic = Plan.builder().code(PlanCode.BASIC).monthlyPriceCents(200_000L).build();

            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));
            when(subscriptionRepository.findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(wompiService.createCheckoutUrl(any(), eq(WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION)))
                    .thenReturn(WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout").build());

            WompiCheckoutResponseDTO response = service.initiatePlanPayment(commercial, PlanCode.BASIC, null);

            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout");
            verify(subscriptionRepository).save(any(Subscription.class));
        }

        @Test
        @DisplayName("con suscripción activa vigente: lanza IllegalStateException")
        void withActiveSubscription_throwsIllegalStateException() {
            CommercialDetails commercial = commercial(1L);
            Plan basic = Plan.builder().code(PlanCode.BASIC).monthlyPriceCents(200_000L).build();
            Subscription active = Subscription.builder().status(SubscriptionStatus.ACTIVE)
                    .endDate(java.time.ZonedDateTime.now().plusDays(10)).build();

            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));
            when(subscriptionRepository.findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.initiatePlanPayment(commercial, PlanCode.BASIC, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("plan BASIC sin precio configurado: lanza IllegalStateException")
        void withoutConfiguredPrice_throwsIllegalStateException() {
            CommercialDetails commercial = commercial(1L);
            Plan basic = Plan.builder().code(PlanCode.BASIC).monthlyPriceCents(null).build();

            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));
            when(subscriptionRepository.findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiatePlanPayment(commercial, PlanCode.BASIC, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("initiatePlanPayment — STANDARD/PREMIUM")
    class InitiateInvestment {

        @Test
        @DisplayName("monto dentro del rango: crea Investment pendiente (wallet ya existente) y genera checkout")
        void withinRange_createsInvestmentAndCheckout() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD)
                    .minInvestmentCents(1_000_000L).maxInvestmentCents(9_999_999L).build();
            Wallet wallet = new Wallet();
            wallet.setId(5L);

            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));
            when(walletRepository.findByCommercialId(1L)).thenReturn(Optional.of(wallet));
            when(wompiService.createCheckoutUrl(any(), eq(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)))
                    .thenReturn(WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout-deposit").build());

            WompiCheckoutResponseDTO response = service.initiatePlanPayment(commercial, PlanCode.STANDARD, 3_000_000L);

            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout-deposit");
            verify(investmentRepository).save(any(Investment.class));
            verify(walletService, never()).createFor(any()); // el wallet ya existía
        }

        @Test
        @DisplayName("sin wallet previo: lo crea antes de registrar el Investment")
        void withoutExistingWallet_createsItFirst() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).minInvestmentCents(1_000_000L).build();
            Wallet newWallet = new Wallet();

            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));
            when(walletRepository.findByCommercialId(1L)).thenReturn(Optional.empty());
            when(walletService.createFor(1L)).thenReturn(newWallet);
            when(wompiService.createCheckoutUrl(any(), any()))
                    .thenReturn(WompiCheckoutResponseDTO.builder().build());

            service.initiatePlanPayment(commercial, PlanCode.STANDARD, 3_000_000L);

            verify(walletService).createFor(1L);
        }

        @Test
        @DisplayName("monto por debajo del mínimo: lanza IllegalArgumentException")
        void belowMinimum_throwsIllegalArgumentException() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).minInvestmentCents(1_000_000L).build();
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            assertThatThrownBy(() -> service.initiatePlanPayment(commercial, PlanCode.STANDARD, 500_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("monto por encima del máximo: lanza IllegalArgumentException")
        void aboveMaximum_throwsIllegalArgumentException() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).maxInvestmentCents(9_999_999L).build();
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            assertThatThrownBy(() -> service.initiatePlanPayment(commercial, PlanCode.STANDARD, 10_000_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("handleWompiResult")
    class HandleWompiResult {

        @Test
        @DisplayName("APPROVED + CHARGE_PLAN_SUBSCRIPTION: activa la suscripción, asigna plan BASIC y distribuye en tesorería")
        void approvedSubscription_activatesAndDistributes() {
            CommercialDetails commercial = commercial(1L);
            Subscription sub = Subscription.builder().commercial(commercial)
                    .status(SubscriptionStatus.PENDING_PAYMENT).build();
            Plan basic = Plan.builder().code(PlanCode.BASIC).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION)
                    .status(WompiTransactionStatus.APPROVED)
                    .reference("VG-SUB-123").amountInCents(200_000L).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(subscriptionRepository.findByWompiReference("VG-SUB-123")).thenReturn(Optional.of(sub));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            service.handleWompiResult(tx.getId());

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(commercial.getCurrentPlan()).isSameAs(basic);
            verify(treasuryService).distributeSubscription(200_000L, commercial, tx.getId());
        }

        @Test
        @DisplayName("APPROVED + CHARGE_BUSINESS_DEPOSIT: confirma la inversión, acredita el wallet y NUNCA cambia el plan del comercial")
        void approvedInvestment_confirmsAndNeverChangesPlan() {
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            CommercialDetails commercial = commercial(1L);
            commercial.setWallet(wallet);
            wallet.setCommercial(commercial);

            Plan standard = Plan.builder().code(PlanCode.STANDARD).build();
            commercial.setCurrentPlan(standard);

            Investment investment = Investment.builder().wallet(wallet).confirmed(false)
                    .planAtDeposit(standard).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)
                    .status(WompiTransactionStatus.APPROVED)
                    .reference("VG-DEP-123").amountInCents(11_000_000L).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReference("VG-DEP-123")).thenReturn(Optional.of(investment));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);

            service.handleWompiResult(tx.getId());

            assertThat(investment.getConfirmed()).isTrue();
            assertThat(wallet.getBalanceCents()).isEqualTo(6_600_000L); // 60% de 11.000.000
            assertThat(commercial.getCurrentPlan()).isSameAs(standard); // la recarga no cambia el plan
            verify(treasuryService).distributeDeposit(11_000_000L, commercial, tx.getId());
            verify(planRepository, never()).findByCodeAndActiveTrue(PlanCode.PREMIUM);
        }

        @Test
        @DisplayName("APPROVED + CHARGE_BUSINESS_DEPOSIT: si es el pago de activación del registro "
                + "(onboarding en PAYMENT_PENDING), asigna el plan del depósito y completa el onboarding")
        void approvedInvestment_initialOnboardingPayment_setsPlanAndCompletesOnboarding() {
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            CommercialDetails commercial = commercial(1L);
            commercial.setWallet(wallet);
            wallet.setCommercial(commercial);
            assertThat(commercial.getCurrentPlan()).isNull();

            Plan standard = Plan.builder().code(PlanCode.STANDARD).build();
            Investment investment = Investment.builder().wallet(wallet).confirmed(false)
                    .planAtDeposit(standard).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)
                    .status(WompiTransactionStatus.APPROVED)
                    .reference("VG-DEP-999").amountInCents(11_000_000L).build();

            com.verygana2.models.commercial.CommercialOnboarding onboarding =
                    new com.verygana2.models.commercial.CommercialOnboarding();
            onboarding.setCurrentStep(com.verygana2.models.enums.commercial.OnboardingStep.PAYMENT_PENDING);

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReference("VG-DEP-999")).thenReturn(Optional.of(investment));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(onboardingRepository.findByCommercialDetails_Id(1L)).thenReturn(Optional.of(onboarding));

            service.handleWompiResult(tx.getId());

            assertThat(commercial.getCurrentPlan()).isSameAs(standard);
            assertThat(onboarding.getCurrentStep()).isEqualTo(com.verygana2.models.enums.commercial.OnboardingStep.COMPLETED);
            assertThat(onboarding.getCompletedAt()).isNotNull();
            // Wallet nueva arranca en 0 (wasExhausted=true), pero es la activación inicial,
            // no una reactivación tras agotamiento — no debe mandar el correo de "saldo restaurado".
            verify(investmentService, never()).handleWalletReplenished(anyLong());
        }

        @Test
        @DisplayName("APPROVED + CHARGE_BUSINESS_DEPOSIT: si el wallet SÍ estaba agotado tras uso previo "
                + "(onboarding ya COMPLETED), notifica la reactivación")
        void approvedInvestment_realExhaustionRecharge_notifiesReplenished() {
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            CommercialDetails commercial = commercial(1L);
            commercial.setWallet(wallet);
            wallet.setCommercial(commercial);

            Plan standard = Plan.builder().code(PlanCode.STANDARD).build();
            commercial.setCurrentPlan(standard);

            Investment investment = Investment.builder().wallet(wallet).confirmed(false)
                    .planAtDeposit(standard).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)
                    .status(WompiTransactionStatus.APPROVED)
                    .reference("VG-DEP-777").amountInCents(11_000_000L).build();

            com.verygana2.models.commercial.CommercialOnboarding onboarding =
                    new com.verygana2.models.commercial.CommercialOnboarding();
            onboarding.setCurrentStep(com.verygana2.models.enums.commercial.OnboardingStep.COMPLETED);

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReference("VG-DEP-777")).thenReturn(Optional.of(investment));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(onboardingRepository.findByCommercialDetails_Id(1L)).thenReturn(Optional.of(onboarding));

            service.handleWompiResult(tx.getId());

            assertThat(commercial.getCurrentPlan()).isSameAs(standard);
            verify(investmentService).handleWalletReplenished(1L);
        }

        @Test
        @DisplayName("DECLINED: marca la Subscription asociada como PAYMENT_FAILED si existe")
        void declined_marksSubscriptionAsPaymentFailed() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.PENDING_PAYMENT)
                    .commercial(commercial(1L)).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION)
                    .status(WompiTransactionStatus.DECLINED)
                    .reference("VG-SUB-404").build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(subscriptionRepository.findByWompiReference("VG-SUB-404")).thenReturn(Optional.of(sub));

            service.handleWompiResult(tx.getId());

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("DECLINED: marca el Investment asociado como fallido (failedAt) si existe")
        void declined_marksInvestmentAsFailed() {
            Wallet wallet = new Wallet();
            wallet.setCommercial(commercial(1L));
            Investment investment = Investment.builder().confirmed(false).wallet(wallet).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)
                    .status(WompiTransactionStatus.DECLINED)
                    .reference("VG-DEP-404").build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(subscriptionRepository.findByWompiReference("VG-DEP-404")).thenReturn(Optional.empty());
            when(investmentRepository.findByWompiReference("VG-DEP-404")).thenReturn(Optional.of(investment));

            service.handleWompiResult(tx.getId());

            assertThat(investment.getFailedAt()).isNotNull();
            assertThat(investment.getConfirmed()).isFalse();
        }

        @Test
        @DisplayName("WompiTransaction inexistente: lanza IllegalStateException")
        void unknownTransaction_throwsIllegalStateException() {
            UUID id = UUID.randomUUID();
            when(wompiTransactionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleWompiResult(id)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getPaymentStatus")
    class GetPaymentStatus {

        @Test
        @DisplayName("suscripción de OTRO comercial: lanza IllegalArgumentException (oculta la existencia)")
        void subscriptionOfAnotherCommercial_throwsIllegalArgumentException() {
            CommercialDetails owner = commercial(1L);
            CommercialDetails requester = commercial(2L);
            Subscription sub = Subscription.builder().commercial(owner).status(SubscriptionStatus.ACTIVE)
                    .plan(Plan.builder().code(PlanCode.BASIC).build()).build();

            when(subscriptionRepository.findByWompiReference("REF")).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.getPaymentStatus("REF", requester))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("referencia inexistente en ningún lado: lanza IllegalArgumentException")
        void unknownReference_throwsIllegalArgumentException() {
            when(subscriptionRepository.findByWompiReference("XYZ")).thenReturn(Optional.empty());
            when(investmentRepository.findByWompiReference("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPaymentStatus("XYZ", commercial(1L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Investment con failedAt: reporta planStatus PAYMENT_FAILED en vez de quedarse en PENDING_PAYMENT")
        void failedInvestment_reportsPaymentFailed() {
            CommercialDetails owner = commercial(1L);
            Wallet wallet = new Wallet();
            wallet.setCommercial(owner);
            Investment investment = Investment.builder()
                    .wallet(wallet)
                    .confirmed(false)
                    .planAtDeposit(Plan.builder().code(PlanCode.STANDARD).build())
                    .build();
            investment.fail(WompiTransaction.builder().status(WompiTransactionStatus.DECLINED).build());

            when(subscriptionRepository.findByWompiReference("VG-DEP-404")).thenReturn(Optional.empty());
            when(investmentRepository.findByWompiReference("VG-DEP-404")).thenReturn(Optional.of(investment));

            var status = service.getPaymentStatus("VG-DEP-404", owner);

            assertThat(status.getPlanStatus()).isEqualTo("PAYMENT_FAILED");
        }
    }

    @Nested
    @DisplayName("getEffectivePlanState")
    class GetEffectivePlanState {

        @Test
        @DisplayName("sin plan asignado: retorna todos los valores por defecto en cero/false")
        void noPlan_returnsDefaults() {
            CommercialDetails commercial = commercial(1L);
            commercial.setCurrentPlan(null);

            var state = service.getEffectivePlanState(commercial);

            assertThat(state.isHasActivePlan()).isFalse();
            assertThat(state.getWalletStatus()).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("STANDARD/PREMIUM: usa el saldo del wallet como presupuesto restante")
        void standardPlan_usesWalletBalance() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).saleCommissionPct(10).maxKeysPct(35).build();
            commercial.setCurrentPlan(standard);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(300_000L);
            wallet.setStatus(com.verygana2.models.enums.finance.WalletStatus.ACTIVE);
            commercial.setWallet(wallet);

            var state = service.getEffectivePlanState(commercial);

            assertThat(state.getRemainingBudgetCents()).isEqualTo(300_000L);
            assertThat(state.getWalletStatus()).isEqualTo("ACTIVE");
            assertThat(state.getMaxKeysPct()).isEqualTo(35);
            assertThat(state.isHasActivePlan()).isTrue();
            assertThat(state.isBudgetSuspended()).isFalse();
        }

        @Test
        @DisplayName("STANDARD/PREMIUM con saldo agotado: hasActivePlan sigue true, budgetSuspended true")
        void standardPlan_exhaustedWallet_keepsPlanActiveButSuspendsBudget() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).saleCommissionPct(10).maxKeysPct(35).build();
            commercial.setCurrentPlan(standard);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            wallet.setStatus(com.verygana2.models.enums.finance.WalletStatus.EXHAUSTED);
            commercial.setWallet(wallet);

            var state = service.getEffectivePlanState(commercial);

            assertThat(state.isHasActivePlan()).isTrue();
            assertThat(state.isBudgetSuspended()).isTrue();
            assertThat(state.isBudgetDormant()).isFalse();
            assertThat(state.getWalletStatus()).isEqualTo("EXHAUSTED");
            assertThat(state.getRemainingBudgetCents()).isZero();
        }

        @Test
        @DisplayName("saldo agotado más allá del periodo de gracia del plan: budgetDormant true")
        void standardPlan_exhaustedBeyondGrace_setsBudgetDormant() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).saleCommissionPct(10).maxKeysPct(35).build();
            commercial.setCurrentPlan(standard);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            wallet.setStatus(com.verygana2.models.enums.finance.WalletStatus.EXHAUSTED);
            wallet.setExhaustedSince(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusDays(20));
            commercial.setWallet(wallet);
            when(effectivePlanResolver.resolveGracePeriodDays(standard)).thenReturn(15);

            var state = service.getEffectivePlanState(commercial);

            assertThat(state.isBudgetSuspended()).isTrue();
            assertThat(state.isBudgetDormant()).isTrue();
        }

        @Test
        @DisplayName("saldo agotado dentro del periodo de gracia: budgetDormant false")
        void standardPlan_exhaustedWithinGrace_notDormant() {
            CommercialDetails commercial = commercial(1L);
            Plan standard = Plan.builder().code(PlanCode.STANDARD).saleCommissionPct(10).maxKeysPct(35).build();
            commercial.setCurrentPlan(standard);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            wallet.setStatus(com.verygana2.models.enums.finance.WalletStatus.EXHAUSTED);
            wallet.setExhaustedSince(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusDays(3));
            commercial.setWallet(wallet);
            when(effectivePlanResolver.resolveGracePeriodDays(standard)).thenReturn(15);

            var state = service.getEffectivePlanState(commercial);

            assertThat(state.isBudgetSuspended()).isTrue();
            assertThat(state.isBudgetDormant()).isFalse();
        }
    }
}
