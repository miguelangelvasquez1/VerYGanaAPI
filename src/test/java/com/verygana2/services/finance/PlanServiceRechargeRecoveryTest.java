package com.verygana2.services.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO;
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO.NextAction;
import com.verygana2.dtos.finance.plans.responses.RechargePreviewResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.RechargeAlreadyPaidException;
import com.verygana2.exceptions.wompi.WompiApiException;
import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
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
 * Recuperación de una recarga interrumpida en {@link PlanServiceImpl}: el comercial
 * firmó el otrosí (y quizá abrió el checkout) pero no terminó de pagar. Debe poder
 * retomarla, reintentar el pago o cancelarla, y la recarga vence sola pasado el
 * plazo — siempre conciliando antes con Wompi para no cancelar ni volver a cobrar
 * un pago cuyo webhook nunca llegó.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanServiceImpl — recarga interrumpida")
class PlanServiceRechargeRecoveryTest {

    private static final Long COMMERCIAL_ID = 1L;
    private static final Long CONTRACT_ID = 50L;
    private static final String OLD_REFERENCE = "VG-DEP-OLD";
    private static final long AMOUNT_CENTS = 100_000_000L; // $1.000.000
    private static final long VAT_CENTS = 19_000_000L;

    @Mock private WompiService wompiService;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private com.verygana2.services.interfaces.finance.ProsperityService prosperityService;
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
    @Mock private CommercialContractService commercialContractService;
    @Mock private CommercialContractRepository commercialContractRepository;
    @Mock private PlanChangeRequestRepository planChangeRequestRepository;
    @Mock private com.verygana2.services.interfaces.finance.PlanChangeRequestService planChangeRequestService;
    @Mock private com.verygana2.mappers.CommercialOnboardingMapper commercialOnboardingMapper;

    private PlanServiceImpl service;
    private CommercialDetails commercial;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new PlanServiceImpl(wompiService, wompiTransactionRepository, commercialDetailsRepository,
                treasuryService, prosperityService, treasuryConfig, subscriptionRepository, investmentRepository, planRepository,
                walletRepository, walletService, onboardingRepository, investmentService, effectivePlanResolver,
                emailService, commercialContractService, commercialContractRepository, planChangeRequestRepository,
                planChangeRequestService, commercialOnboardingMapper);
        ReflectionTestUtils.setField(service, "rechargeMaxAgeHours", 24L);

        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        User user = new User();
        user.setId(COMMERCIAL_ID);
        user.setEmail("comercial@test.com");
        user.setPublicId(UUID.randomUUID());
        commercial.setUser(user);
        commercial.setCurrentPlan(Plan.builder().code(PlanCode.STANDARD)
                .minInvestmentCents(50_000_000L).maxInvestmentCents(500_000_000L).build());

        wallet = new Wallet();
        wallet.setBalanceCents(60_000_000L);
        wallet.setCommercial(commercial);
        commercial.setWallet(wallet);
    }

    private CommercialContract signedContract(Investment investment) {
        CommercialContract contract = new CommercialContract();
        contract.setId(CONTRACT_ID);
        contract.setCommercial(commercial);
        contract.setPurpose(ContractPurpose.RECHARGE);
        contract.setStatus(ContractStatus.SIGNED);
        contract.setAmountCentsSnapshot(AMOUNT_CENTS);
        contract.setGeneratedAt(ZonedDateTime.now().minusHours(30));
        contract.setInvestment(investment);
        return contract;
    }

    private Investment pendingInvestment() {
        return Investment.builder().wallet(wallet).planAtDeposit(commercial.getCurrentPlan())
                .wompiReference(OLD_REFERENCE).depositAmountCents(AMOUNT_CENTS).vatAmountCents(VAT_CENTS)
                .confirmed(false).build();
    }

    private WompiTransactionData wompiData(String status) {
        WompiTransactionData data = new WompiTransactionData();
        data.setId("wompi-123");
        data.setStatus(status);
        data.setReference(OLD_REFERENCE);
        data.setCreatedAt("2026-10-02T15:00:00.000Z");
        return data;
    }

    private void wompiReports(String status) {
        when(wompiService.reconcileByReference(OLD_REFERENCE))
                .thenReturn(status == null ? Optional.empty() : Optional.of(wompiData(status)));
    }

    @Nested
    @DisplayName("generateRechargeCheckout — reintento")
    class Checkout {

        @Test
        @DisplayName("checkout abierto y nunca pagado: retoma la MISMA referencia y el mismo Investment, sin crear otro")
        void abandonedCheckout_resumesSameReference() {
            Investment previous = pendingInvestment();
            CommercialContract contract = signedContract(previous);
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            wompiReports(null);
            when(wompiService.resumeCheckoutUrl(any(), eq(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)))
                    .thenReturn(WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout").build());

            WompiCheckoutResponseDTO response = service.generateRechargeCheckout(CONTRACT_ID, commercial);

            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout");
            ArgumentCaptor<WompiCheckoutRequestDTO> request = ArgumentCaptor.forClass(WompiCheckoutRequestDTO.class);
            verify(wompiService).resumeCheckoutUrl(request.capture(), eq(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT));
            assertThat(request.getValue().getReference()).isEqualTo(OLD_REFERENCE);
            assertThat(request.getValue().getAmountInCents()).isEqualTo(AMOUNT_CENTS + VAT_CENTS);
            assertThat(contract.getInvestment()).isSameAs(previous);
            verify(investmentRepository, never()).save(any());
            verify(wompiService, never()).createCheckoutUrl(any(), any());
        }

        @Test
        @DisplayName("pago anterior rechazado: genera un Investment y una referencia nuevos y los vincula al contrato")
        void declinedPayment_createsNewInvestment() {
            Investment previous = pendingInvestment();
            previous.setFailedAt(ZonedDateTime.now().minusHours(2));
            CommercialContract contract = signedContract(previous);
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            wompiReports("DECLINED");
            when(treasuryConfig.getVatPct()).thenReturn(19);
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(wompiService.createCheckoutUrl(any(), eq(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT)))
                    .thenReturn(WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout-nuevo").build());

            WompiCheckoutResponseDTO response = service.generateRechargeCheckout(CONTRACT_ID, commercial);

            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout-nuevo");
            assertThat(contract.getInvestment()).isNotSameAs(previous);
            assertThat(contract.getInvestment().getWompiReference()).isNotEqualTo(OLD_REFERENCE);
            assertThat(contract.getInvestment().getDepositAmountCents()).isEqualTo(AMOUNT_CENTS);
            assertThat(contract.getInvestment().getVatAmountCents()).isEqualTo(VAT_CENTS);
            verify(wompiService, never()).resumeCheckoutUrl(any(), any());
        }

        @Test
        @DisplayName("Wompi lo reporta APPROVED y el webhook nunca llegó: acredita el saldo y NO deja pagar otra vez")
        void approvedWithoutWebhook_creditsAndRefusesSecondCharge() {
            Investment previous = pendingInvestment();
            CommercialContract contract = signedContract(previous);
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT).status(WompiTransactionStatus.APPROVED)
                    .reference(OLD_REFERENCE).amountInCents(AMOUNT_CENTS + VAT_CENTS).build();

            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            wompiReports("APPROVED");
            when(wompiService.updateTransactionFromWebhook(eq("wompi-123"), eq(OLD_REFERENCE), eq("APPROVED"), any(), any()))
                    .thenReturn(tx);
            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReferenceForUpdate(OLD_REFERENCE)).thenReturn(Optional.of(previous));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);

            assertThatThrownBy(() -> service.generateRechargeCheckout(CONTRACT_ID, commercial))
                    .isInstanceOf(RechargeAlreadyPaidException.class);

            assertThat(previous.getConfirmed()).isTrue();
            assertThat(wallet.getBalanceCents()).isEqualTo(60_000_000L + 60_000_000L); // + 60% de $1.000.000
            verify(treasuryService).distributeDeposit(AMOUNT_CENTS, VAT_CENTS, commercial, tx.getId());
            verify(wompiService, never()).createCheckoutUrl(any(), any());
            verify(wompiService, never()).resumeCheckoutUrl(any(), any());
        }

        @Test
        @DisplayName("pago anterior todavía en proceso en Wompi: no genera otro checkout")
        void paymentInProcess_refuses() {
            CommercialContract contract = signedContract(pendingInvestment());
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            wompiReports("PENDING");

            assertThatThrownBy(() -> service.generateRechargeCheckout(CONTRACT_ID, commercial))
                    .isInstanceOf(BusinessException.class)
                    .isNotInstanceOf(RechargeAlreadyPaidException.class);

            verify(wompiService, never()).createCheckoutUrl(any(), any());
            verify(wompiService, never()).resumeCheckoutUrl(any(), any());
        }
    }

    @Nested
    @DisplayName("cancelRecharge")
    class Cancel {

        @Test
        @DisplayName("checkout abierto y nunca pagado: se puede cancelar")
        void abandonedCheckout_cancels() {
            CommercialContract contract = signedContract(pendingInvestment());
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            wompiReports(null);

            service.cancelRecharge(CONTRACT_ID, commercial);

            verify(commercialContractService).cancelForCommercial(CONTRACT_ID, COMMERCIAL_ID);
        }

        @Test
        @DisplayName("firmada y sin checkout: cancela sin consultar a Wompi")
        void signedWithoutCheckout_cancelsWithoutCallingWompi() {
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(signedContract(null)));

            service.cancelRecharge(CONTRACT_ID, commercial);

            verify(commercialContractService).cancelForCommercial(CONTRACT_ID, COMMERCIAL_ID);
            verify(wompiService, never()).reconcileByReference(any());
        }

        @Test
        @DisplayName("pago en proceso en Wompi: NO cancela")
        void paymentInProcess_doesNotCancel() {
            when(commercialContractRepository.findById(CONTRACT_ID))
                    .thenReturn(Optional.of(signedContract(pendingInvestment())));
            wompiReports("PENDING");

            assertThatThrownBy(() -> service.cancelRecharge(CONTRACT_ID, commercial))
                    .isInstanceOf(BusinessException.class);

            verify(commercialContractService, never()).cancelForCommercial(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Wompi no responde: NO cancela — no poder consultar no equivale a 'no hay pago'")
        void wompiUnreachable_doesNotCancel() {
            when(commercialContractRepository.findById(CONTRACT_ID))
                    .thenReturn(Optional.of(signedContract(pendingInvestment())));
            when(wompiService.reconcileByReference(OLD_REFERENCE)).thenThrow(new WompiApiException("timeout", 503));

            assertThatThrownBy(() -> service.cancelRecharge(CONTRACT_ID, commercial))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No pudimos verificar");

            verify(commercialContractService, never()).cancelForCommercial(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("expireRecharge")
    class Expire {

        @Test
        @DisplayName("sin pago en Wompi: vence la recarga")
        void noPayment_expires() {
            when(commercialContractRepository.findById(CONTRACT_ID))
                    .thenReturn(Optional.of(signedContract(pendingInvestment())));
            wompiReports(null);

            assertThat(service.expireRecharge(CONTRACT_ID)).isTrue();

            verify(commercialContractService).expireRecharge(CONTRACT_ID);
        }

        @Test
        @DisplayName("pago en proceso en Wompi: NO la vence")
        void paymentInProcess_doesNotExpire() {
            when(commercialContractRepository.findById(CONTRACT_ID))
                    .thenReturn(Optional.of(signedContract(pendingInvestment())));
            wompiReports("PENDING");

            assertThat(service.expireRecharge(CONTRACT_ID)).isFalse();

            verify(commercialContractService, never()).expireRecharge(anyLong());
        }

        @Test
        @DisplayName("ya cancelada entre la consulta y la ejecución: no hace nada")
        void alreadyCancelled_noop() {
            CommercialContract contract = signedContract(null);
            contract.setStatus(ContractStatus.CANCELLED);
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

            assertThat(service.expireRecharge(CONTRACT_ID)).isFalse();

            verify(commercialContractService, never()).expireRecharge(anyLong());
        }
    }

    @Nested
    @DisplayName("estado de la recarga en curso")
    class OpenState {

        @Test
        @DisplayName("previewRecharge con una recarga firmada sin pagar: no elegible, y devuelve el contractId y el paso PAY")
        void preview_exposesOpenRecharge() {
            when(commercialContractRepository.findOpenRechargeContracts(COMMERCIAL_ID))
                    .thenReturn(List.of(signedContract(null)));
            when(treasuryConfig.getVatPct()).thenReturn(19);
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);

            RechargePreviewResponseDTO preview = service.previewRecharge(commercial, AMOUNT_CENTS);

            assertThat(preview.isEligible()).isFalse();
            OpenRechargeResponseDTO open = preview.getOpenRecharge();
            assertThat(open).isNotNull();
            assertThat(open.getContractId()).isEqualTo(CONTRACT_ID);
            assertThat(open.getNextAction()).isEqualTo(NextAction.PAY);
            assertThat(open.getTotalToPayPesos()).isEqualTo(1_190_000L);
            assertThat(open.isPaymentAttempted()).isFalse();
            assertThat(preview.getMessage()).isEqualTo(open.getMessage()).contains("pendiente de pago");
        }

        @Test
        @DisplayName("getOpenRecharge: pendiente de firma → SIGN; pago rechazado → RETRY_PAYMENT")
        void getOpenRecharge_nextActionByState() {
            CommercialContract unsigned = signedContract(null);
            unsigned.setStatus(ContractStatus.PENDING_SIGNATURE);
            when(commercialContractRepository.findOpenRechargeContracts(COMMERCIAL_ID)).thenReturn(List.of(unsigned));
            when(treasuryConfig.getVatPct()).thenReturn(19);

            assertThat(service.getOpenRecharge(commercial)).get()
                    .extracting(OpenRechargeResponseDTO::getNextAction).isEqualTo(NextAction.SIGN);

            Investment failed = pendingInvestment();
            failed.setFailedAt(ZonedDateTime.now());
            when(commercialContractRepository.findOpenRechargeContracts(COMMERCIAL_ID))
                    .thenReturn(List.of(signedContract(failed)));

            assertThat(service.getOpenRecharge(commercial)).get()
                    .extracting(OpenRechargeResponseDTO::getNextAction).isEqualTo(NextAction.RETRY_PAYMENT);
        }

        @Test
        @DisplayName("getOpenRecharge sin recargas en curso: vacío")
        void getOpenRecharge_none() {
            when(commercialContractRepository.findOpenRechargeContracts(COMMERCIAL_ID)).thenReturn(List.of());

            assertThat(service.getOpenRecharge(commercial)).isEmpty();
        }

        @Test
        @DisplayName("reconcileRecharge con el pago aprobado sin webhook: acredita y devuelve vacío (ya no está en curso)")
        void reconcile_approved_creditsAndReturnsEmpty() {
            Investment previous = pendingInvestment();
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .type(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT).status(WompiTransactionStatus.APPROVED)
                    .reference(OLD_REFERENCE).amountInCents(AMOUNT_CENTS + VAT_CENTS).build();

            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(signedContract(previous)));
            wompiReports("APPROVED");
            when(wompiService.updateTransactionFromWebhook(eq("wompi-123"), eq(OLD_REFERENCE), eq("APPROVED"), any(), any()))
                    .thenReturn(tx);
            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(investmentRepository.findByWompiReferenceForUpdate(OLD_REFERENCE)).thenReturn(Optional.of(previous));
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);

            assertThat(service.reconcileRecharge(CONTRACT_ID, commercial)).isEmpty();
            assertThat(previous.getConfirmed()).isTrue();
        }

        @Test
        @DisplayName("reconcileRecharge con el pago en proceso: sigue en curso con WAIT_PAYMENT")
        void reconcile_inProcess_waitPayment() {
            when(commercialContractRepository.findById(CONTRACT_ID))
                    .thenReturn(Optional.of(signedContract(pendingInvestment())));
            wompiReports("PENDING");

            assertThat(service.reconcileRecharge(CONTRACT_ID, commercial)).get()
                    .extracting(OpenRechargeResponseDTO::getNextAction).isEqualTo(NextAction.WAIT_PAYMENT);
        }

        @Test
        @DisplayName("reconcileRecharge sobre un contrato de otro comercial: oculta su existencia")
        void reconcile_otherCommercial_notFound() {
            CommercialDetails other = new CommercialDetails();
            other.setId(99L);
            when(commercialContractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(signedContract(null)));

            assertThatThrownBy(() -> service.reconcileRecharge(CONTRACT_ID, other))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
