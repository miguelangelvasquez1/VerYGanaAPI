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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanServiceImpl}: iniciar el pago de un plan (BASIC vs.
 * STANDARD/PREMIUM tienen validaciones y entidades distintas) y procesar el
 * resultado del webhook de Wompi (activación de suscripción o inversión, y
 * la regla de que el plan del comercial nunca baja).
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

    private PlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanServiceImpl(wompiService, wompiTransactionRepository, commercialDetailsRepository,
                treasuryService, treasuryConfig, subscriptionRepository, investmentRepository, planRepository,
                walletRepository, walletService);
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
        @DisplayName("APPROVED + CHARGE_BUSINESS_DEPOSIT: confirma la inversión, acredita el wallet y NUNCA degrada el plan")
        void approvedInvestment_confirmsAndUpgradesButNeverDowngrades() {
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            CommercialDetails commercial = commercial(1L);
            commercial.setWallet(wallet);
            wallet.setCommercial(commercial);

            Investment investment = Investment.builder().wallet(wallet).confirmed(false).build();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)
                    .status(WompiTransactionStatus.APPROVED)
                    .reference("VG-DEP-123").amountInCents(11_000_000L).build();

            Plan premium = Plan.builder().code(PlanCode.PREMIUM).minInvestmentCents(1_000_000_000L).build();
            Plan standard = Plan.builder().code(PlanCode.STANDARD).minInvestmentCents(10_000_000L).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReference("VG-DEP-123")).thenReturn(Optional.of(investment));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(investmentRepository.findByWalletAndConfirmedTrue(wallet))
                    .thenReturn(java.util.List.of(Investment.builder().depositAmountCents(11_000_000L).build()));
            // 11.000.000 centavos < umbral PREMIUM (1.000.000.000) → no alcanza el umbral real de negocio, pero
            // aquí bajamos el mínimo de STANDARD a 10.000.000 para forzar que SÍ lo alcance y así probar el
            // camino de "confirma y sube de plan" sin depender de los montos reales de producción.
            when(planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM)).thenReturn(Optional.of(premium));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            service.handleWompiResult(tx.getId());

            assertThat(investment.getConfirmed()).isTrue();
            assertThat(wallet.getBalanceCents()).isEqualTo(6_600_000L); // 60% de 11.000.000
            assertThat(commercial.getCurrentPlan()).isSameAs(standard);
            verify(treasuryService).distributeDeposit(11_000_000L, commercial, tx.getId());
        }

        @Test
        @DisplayName("DECLINED: marca la Subscription asociada como PAYMENT_FAILED si existe")
        void declined_marksSubscriptionAsPaymentFailed() {
            Subscription sub = Subscription.builder().status(SubscriptionStatus.PENDING_PAYMENT).build();
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
        }
    }
}
