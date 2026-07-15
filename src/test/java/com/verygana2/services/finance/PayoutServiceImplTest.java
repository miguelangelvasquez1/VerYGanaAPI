package com.verygana2.services.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.kushki.KushkiTokenResponseDTO;
import com.verygana2.dtos.kushki.KushkiTransferResponseDTO;
import com.verygana2.dtos.kushki.KushkiWebhookEvent;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.KushkiTransactionStatus;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KushkiTransaction;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KushkiTransactionRepository;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.kushki.KushkiPayoutClient;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutServiceImpl}: el job de payouts en sus 2 fases
 * (agrupar copagos por comercial → transferir vía Kushki) y el webhook que
 * confirma cada transferencia. Kushki está mockeado — estos tests no
 * requieren credenciales reales, solo verifican que el servicio orquesta
 * correctamente lo que ya está implementado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutServiceImpl")
class PayoutServiceImplTest {

    @Mock private PayoutRepository payoutRepository;
    @Mock private PayoutItemRepository payoutItemRepository;
    @Mock private CopaymentRepository copaymentRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private KushkiPayoutClient kushkiPayoutClient;
    @Mock private KushkiTransactionRepository kushkiTransactionRepository;
    @Mock private PayoutMethodRepository payoutMethodRepository;

    private PayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, payoutItemRepository, copaymentRepository, treasuryService,
                kushkiPayoutClient, kushkiTransactionRepository, payoutMethodRepository);
    }

    private CommercialDetails commercial(Long id, String name) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        c.setCompanyName(name);
        return c;
    }

    private Copayment completedCopayment(CommercialDetails commercial, long subtotal, long commission, long net) {
        Product product = new Product();
        product.setCommercial(commercial);
        PurchaseItem item = PurchaseItem.builder().product(product)
                .subtotalCents(subtotal).commissionCents(commission).netToCommercialCents(net)
                .commissionPctApplied(10).build();
        Purchase purchase = Purchase.builder().items(new java.util.ArrayList<>(List.of(item))).build();
        item.setPurchase(purchase);
        return Copayment.builder().id(UUID.randomUUID()).purchase(purchase).status(CopaymentStatus.COMPLETED).build();
    }

    @Nested
    @DisplayName("scheduleDailyPayouts")
    class ScheduleDailyPayouts {

        @Test
        @DisplayName("sin copagos COMPLETED en el período: no crea ningún payout")
        void noCompletedCopayments_createsNothing() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
            when(copaymentRepository.findCompletedInPeriod(CopaymentStatus.COMPLETED, start, start.plusDays(1)))
                    .thenReturn(List.of());

            service.scheduleDailyPayouts(start, start.plusDays(1));

            verify(payoutRepository, never()).save(any());
        }

        @Test
        @DisplayName("agrupa por comercial y crea un Payout con sus PayoutItems")
        void groupsByCommercialAndCreatesPayout() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Copayment copayment = completedCopayment(commercial, 100_000L, 10_000L, 90_000L);

            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
            when(copaymentRepository.findCompletedInPeriod(any(), any(), any())).thenReturn(List.of(copayment));
            when(payoutItemRepository.existsByCopaymentAndCommercial(copayment.getId(), 1L)).thenReturn(false);
            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

            service.scheduleDailyPayouts(start, start.plusDays(1));

            var captor = org.mockito.ArgumentCaptor.forClass(Payout.class);
            verify(payoutRepository).save(captor.capture());
            assertThat(captor.getValue().getNetAmountCents()).isEqualTo(90_000L);
            assertThat(captor.getValue().getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            verify(payoutItemRepository).save(any());
        }

        @Test
        @DisplayName("idempotencia: si ya existe un PayoutItem para (copayment, comercial), lo salta")
        void idempotent_skipsAlreadyPaidCopayments() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Copayment copayment = completedCopayment(commercial, 100_000L, 10_000L, 90_000L);

            when(copaymentRepository.findCompletedInPeriod(any(), any(), any())).thenReturn(List.of(copayment));
            when(payoutItemRepository.existsByCopaymentAndCommercial(copayment.getId(), 1L)).thenReturn(true);

            service.scheduleDailyPayouts(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));

            verify(payoutRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("processScheduledPayouts")
    class ProcessScheduledPayouts {

        private PayoutMethod verifiedMethod() {
            return PayoutMethod.builder().verificationStatus(VerificationStatus.VERIFIED)
                    .accountHolderDocType(PayoutMethod.DocType.CC).accountHolderDoc("123")
                    .accountHolderName("Juan").bankCode("1007")
                    .bankAccountType(PayoutMethod.BankAccountType.SAVINGS).accountNumber("999").build();
        }

        @Test
        @DisplayName("sin payouts SCHEDULED: no hace nada")
        void noScheduled_doesNothing() {
            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

            service.processScheduledPayouts();

            verify(kushkiPayoutClient, never()).tokenizeAccount(any());
        }

        @Test
        @DisplayName("transferencia exitosa: el payout pasa a PROCESSING y queda vinculado a la KushkiTransaction")
        void successfulTransfer_movesToProcessing() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));

            KushkiTokenResponseDTO tokenResponse = new KushkiTokenResponseDTO();
            tokenResponse.setToken("tok_123");
            when(kushkiPayoutClient.tokenizeAccount(any())).thenReturn(tokenResponse);

            KushkiTransferResponseDTO transferResponse = new KushkiTransferResponseDTO();
            transferResponse.setTransferId("tr_123");
            transferResponse.setCode("000");
            when(kushkiPayoutClient.initiateTransfer(any())).thenReturn(transferResponse);
            when(kushkiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
            assertThat(payout.getKushkiTransaction()).isNotNull();
        }

        @Test
        @DisplayName("sin método de pago verificado: el payout queda FAILED con el motivo, sin detener a los demás")
        void noVerifiedMethod_marksFailedWithoutStoppingOthers() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.empty());

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isNotBlank();
            verify(kushkiPayoutClient, never()).tokenizeAccount(any());
        }

        @Test
        @DisplayName("Kushki rechaza la transferencia (code != 000): el payout queda FAILED con el mensaje de Kushki")
        void kushkiRejectsTransfer_marksFailed() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));

            KushkiTokenResponseDTO tokenResponse = new KushkiTokenResponseDTO();
            tokenResponse.setToken("tok_123");
            when(kushkiPayoutClient.tokenizeAccount(any())).thenReturn(tokenResponse);

            KushkiTransferResponseDTO transferResponse = new KushkiTransferResponseDTO();
            transferResponse.setCode("500");
            transferResponse.setMessage("Cuenta inválida");
            when(kushkiPayoutClient.initiateTransfer(any())).thenReturn(transferResponse);
            when(kushkiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isEqualTo("Cuenta inválida");
        }
    }

    @Test
    @DisplayName("retryFailedPayouts: incrementa retryCount, reintenta y limpia el motivo de fallo previo")
    void retryFailedPayouts_incrementsAndRetries() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(0)
                .failureReason("Error previo").build();

        when(payoutRepository.findFailedForRetry(any(), any(), any())).thenReturn(List.of(payout));
        when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                .thenReturn(Optional.empty()); // vuelve a fallar por falta de método verificado

        service.retryFailedPayouts(ZonedDateTime.now().minusDays(1), ZonedDateTime.now());

        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED); // vuelve a fallar, pero se intentó
    }

    @Nested
    @DisplayName("handleKushkiWebhook")
    class HandleKushkiWebhook {

        private KushkiWebhookEvent event(String status) {
            KushkiWebhookEvent e = new KushkiWebhookEvent();
            e.setTransferId("tr_123");
            e.setMerchantTransferReference("VG-PAYOUT-abc");
            e.setStatus(status);
            e.setMessage(status.equals("DECLINED") ? "Fondos insuficientes" : null);
            return e;
        }

        @Test
        @DisplayName("KushkiTransaction inexistente: no falla, simplemente ignora el evento")
        void unknownTransaction_ignoresSilently() {
            when(kushkiTransactionRepository.findByInternalReference("VG-PAYOUT-abc")).thenReturn(Optional.empty());
            when(kushkiTransactionRepository.findByKushkiTransferId("tr_123")).thenReturn(Optional.empty());

            service.handleKushkiWebhook(event("APPROVED"), Map.of());

            verify(payoutRepository, never()).save(any());
        }

        @Test
        @DisplayName("evento duplicado sobre una transacción ya terminal: se ignora (idempotencia)")
        void alreadyTerminal_ignoresAsIdempotent() {
            KushkiTransaction tx = KushkiTransaction.builder().id(UUID.randomUUID())
                    .status(KushkiTransactionStatus.APPROVED).build();
            when(kushkiTransactionRepository.findByInternalReference("VG-PAYOUT-abc")).thenReturn(Optional.of(tx));

            service.handleKushkiWebhook(event("APPROVED"), Map.of());

            verify(kushkiTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("APPROVED: marca la KushkiTransaction y el Payout como PAID, y registra el envío en tesorería")
        void approved_marksPaidAndRegistersInTreasury() {
            KushkiTransaction tx = KushkiTransaction.builder().id(UUID.randomUUID())
                    .status(KushkiTransactionStatus.PENDING).build();
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).kushkiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(kushkiTransactionRepository.findByInternalReference("VG-PAYOUT-abc")).thenReturn(Optional.of(tx));
            when(payoutRepository.findByKushkiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleKushkiWebhook(event("APPROVED"), Map.of());

            assertThat(tx.getStatus()).isEqualTo(KushkiTransactionStatus.APPROVED);
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
            assertThat(payout.getPaidAt()).isNotNull();
            verify(treasuryService).registerPayoutSent(90_000L, payout.getId());
        }

        @Test
        @DisplayName("DECLINED: marca la KushkiTransaction y el Payout como FAILED con el motivo, sin tocar tesorería")
        void declined_marksFailedWithoutTreasury() {
            KushkiTransaction tx = KushkiTransaction.builder().id(UUID.randomUUID())
                    .status(KushkiTransactionStatus.PENDING).build();
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).kushkiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(kushkiTransactionRepository.findByInternalReference("VG-PAYOUT-abc")).thenReturn(Optional.of(tx));
            when(payoutRepository.findByKushkiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleKushkiWebhook(event("DECLINED"), Map.of());

            assertThat(tx.getStatus()).isEqualTo(KushkiTransactionStatus.DECLINED);
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isEqualTo("Fondos insuficientes");
            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("Payout no encontrado para la KushkiTransaction: lanza EntityNotFoundException")
        void payoutNotFound_throwsEntityNotFoundException() {
            KushkiTransaction tx = KushkiTransaction.builder().id(UUID.randomUUID())
                    .status(KushkiTransactionStatus.PENDING).build();
            when(kushkiTransactionRepository.findByInternalReference("VG-PAYOUT-abc")).thenReturn(Optional.of(tx));
            when(payoutRepository.findByKushkiTransactionId(tx.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleKushkiWebhook(event("APPROVED"), Map.of()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
