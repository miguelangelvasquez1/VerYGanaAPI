package com.verygana2.services.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.metrics.PayoutMetrics;
import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.wompi.WompiPayoutClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutServiceImpl}: el job de payouts en sus 2 fases
 * (agrupar copagos por comercial → transferir vía Wompi Pagos a Terceros) y
 * el webhook que confirma cada transferencia. Wompi está mockeado — estos
 * tests no requieren credenciales reales, solo verifican que el servicio
 * orquesta correctamente lo que ya está implementado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutServiceImpl")
class PayoutServiceImplTest {

    @Mock private PayoutRepository payoutRepository;
    @Mock private PayoutItemRepository payoutItemRepository;
    @Mock private CopaymentRepository copaymentRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private WompiPayoutClient wompiPayoutClient;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private WompiPayoutConfig wompiPayoutConfig;
    @Mock private PayoutMethodRepository payoutMethodRepository;

    private PayoutServiceImpl service;

    /**
     * PayoutMetrics va real y no mockeado: con un SimpleMeterRegistry, cada test del flujo
     * verifica de paso que la instrumentación quedó conectada donde debe. Un mock solo
     * probaría que se llamó a un doble, no que el contador existe con el nombre correcto.
     */
    private SimpleMeterRegistry meterRegistry;
    private PayoutMetrics payoutMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        payoutMetrics = new PayoutMetrics(meterRegistry, payoutRepository);
        payoutMetrics.registerGauges();   // fuera de Spring no corre el @PostConstruct

        service = new PayoutServiceImpl(payoutRepository, payoutItemRepository, copaymentRepository, treasuryService,
                wompiPayoutClient, wompiTransactionRepository, wompiPayoutConfig, payoutMethodRepository,
                payoutMetrics);
    }

    /** Valor actual de un contador, o 0 si nunca se incrementó. */
    private double counter(String name, String... tags) {
        var found = meterRegistry.find(name).tags(tags).counter();
        return found == null ? 0d : found.count();
    }

    private CommercialDetails commercial(Long id, String name) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        c.setCompanyName(name);
        User user = new User();
        user.setEmail("comercial" + id + "@verygana.co");
        c.setUser(user);
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
                    .type(PayoutMethod.PayoutMethodType.BANK_TRANSFER)
                    .accountHolderDocType(PayoutMethod.DocType.CC).accountHolderDoc("123")
                    .accountHolderName("Juan").bankCode("bank-uuid-1007")
                    .bankAccountType(PayoutMethod.BankAccountType.SAVINGS).accountNumber("999").build();
        }

        @Test
        @DisplayName("sin payouts SCHEDULED: no hace nada")
        void noScheduled_doesNothing() {
            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

            service.processScheduledPayouts();

            verify(wompiPayoutClient, never()).createPayout(any());
        }

        @Test
        @DisplayName("transferencia exitosa: el payout pasa a PROCESSING y queda vinculado a la WompiTransaction")
        void successfulTransfer_movesToProcessing() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));
            when(wompiPayoutConfig.getAccountId()).thenReturn("acc_123");

            WompiPayoutResponseDTO response = new WompiPayoutResponseDTO();
            response.setStatus(201);
            response.setCode("OK");
            WompiPayoutResponseDTO.PayoutData data = new WompiPayoutResponseDTO.PayoutData();
            data.setPayoutId("wp_123");
            data.setSuccess(1);
            data.setFailed(0);
            response.setData(data);
            when(wompiPayoutClient.createPayout(any())).thenReturn(response);
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
            assertThat(payout.getWompiTransaction()).isNotNull();
            assertThat(payout.getWompiTransaction().getStatus()).isEqualTo(WompiTransactionStatus.PENDING);
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
            verify(wompiPayoutClient, never()).createPayout(any());
        }

        @Test
        @DisplayName("Wompi rechaza el payout (status != aceptado): el payout queda FAILED con el status de Wompi")
        void wompiRejectsPayout_marksFailed() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));
            when(wompiPayoutConfig.getAccountId()).thenReturn("acc_123");

            WompiPayoutResponseDTO response = new WompiPayoutResponseDTO();
            response.setStatus(400);
            response.setCode("VALIDATION_ERROR");
            response.setMessage("Cuenta destino inválida");
            when(wompiPayoutClient.createPayout(any())).thenReturn(response);
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isEqualTo("VALIDATION_ERROR: Cuenta destino inválida");
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
    @DisplayName("handleWompiResult")
    class HandleWompiResult {

        @Test
        @DisplayName("WompiTransaction inexistente: lanza IllegalArgumentException")
        void unknownTransaction_throwsIllegalArgumentException() {
            UUID txId = UUID.randomUUID();
            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleWompiResult(txId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("evento duplicado sobre un payout que ya no está PROCESSING: se ignora (idempotencia)")
        void alreadyResolved_ignoresAsIdempotent() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.APPROVED).build();
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).wompiTransaction(tx).status(PayoutStatus.PAID).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleWompiResult(tx.getId());

            verify(payoutRepository, never()).save(any());
            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("APPROVED: marca el Payout como PAID y registra el envío en tesorería")
        void approved_marksPaidAndRegistersInTreasury() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.APPROVED).build();
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).wompiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleWompiResult(tx.getId());

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
            assertThat(payout.getPaidAt()).isNotNull();
            verify(treasuryService).registerPayoutSent(90_000L, payout.getId());
        }

        @Test
        @DisplayName("DECLINED: marca el Payout como FAILED con el motivo, sin tocar tesorería")
        void declined_marksFailedWithoutTreasury() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.DECLINED).build();
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).wompiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleWompiResult(tx.getId());

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isEqualTo("DECLINED");
            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("Payout no encontrado para la WompiTransaction: lanza EntityNotFoundException")
        void payoutNotFound_throwsEntityNotFoundException() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.APPROVED).build();
            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleWompiResult(tx.getId()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    /**
     * El motivo de existir de PayoutMetrics: PayoutServiceImpl atrapa la excepción de cada
     * payout dentro del bucle, así que el job termina en SUCCESS con todos sus payouts
     * caídos. Estos tests fijan que los contadores sí registran lo que el job se traga.
     */
    @Nested
    @DisplayName("métricas de negocio")
    class BusinessMetrics {

        private PayoutMethod verifiedMethod() {
            return PayoutMethod.builder().verificationStatus(VerificationStatus.VERIFIED)
                    .type(PayoutMethod.PayoutMethodType.BANK_TRANSFER)
                    .accountHolderDocType(PayoutMethod.DocType.CC).accountHolderDoc("123")
                    .accountHolderName("Juan").bankCode("bank-uuid-1007")
                    .bankAccountType(PayoutMethod.BankAccountType.SAVINGS).accountNumber("999").build();
        }

        @Test
        @DisplayName("crear un payout incrementa el conteo y el monto programado")
        void schedulingCountsPayoutAndAmount() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Copayment copayment = completedCopayment(commercial, 100_000L, 10_000L, 90_000L);

            when(copaymentRepository.findCompletedInPeriod(any(), any(), any())).thenReturn(List.of(copayment));
            when(payoutItemRepository.existsByCopaymentAndCommercial(copayment.getId(), 1L)).thenReturn(false);
            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

            service.scheduleDailyPayouts(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));

            assertThat(counter("payout.scheduled")).isEqualTo(1d);
            assertThat(counter("payout.scheduled.amount.cents")).isEqualTo(90_000d);
            // El helper commercial() no asigna defaultPayoutMethod, así que canReceivePayouts()
            // es false: es exactamente el caso de plata que se debe y nunca sale.
            assertThat(counter("payout.without.method")).isEqualTo(1d);
        }

        @Test
        @DisplayName("la excepción que el bucle se traga queda contada con su fase y su tipo")
        void swallowedExceptionIsCounted() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.empty());

            service.processScheduledPayouts();

            assertThat(counter("payout.processing.errors", "phase", "PROCESS", "exception", "IllegalStateException"))
                    .isEqualTo(1d);
            assertThat(counter("payout.sent", "outcome", "ACCEPTED")).isZero();
        }

        @Test
        @DisplayName("Wompi acepta la transferencia: payout.sent con outcome ACCEPTED")
        void acceptedTransferIsCounted() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));
            when(wompiPayoutConfig.getAccountId()).thenReturn("acc_123");

            WompiPayoutResponseDTO response = new WompiPayoutResponseDTO();
            response.setStatus(201);
            response.setCode("OK");
            WompiPayoutResponseDTO.PayoutData data = new WompiPayoutResponseDTO.PayoutData();
            data.setPayoutId("wp_123");
            data.setSuccess(1);
            data.setFailed(0);
            response.setData(data);
            when(wompiPayoutClient.createPayout(any())).thenReturn(response);
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(counter("payout.sent", "outcome", "ACCEPTED")).isEqualTo(1d);
        }

        @Test
        @DisplayName("webhook APPROVED: cuenta el payout confirmado y el dinero efectivamente dispersado")
        void approvedWebhookCountsPaidAmount() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.APPROVED).build();
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial(1L, "Tienda X"))
                    .netAmountCents(90_000L).wompiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleWompiResult(tx.getId());

            assertThat(counter("payout.confirmed", "outcome", "PAID", "reason", "none")).isEqualTo(1d);
            assertThat(counter("payout.paid.amount.cents")).isEqualTo(90_000d);
        }

        @Test
        @DisplayName("webhook DECLINED: la razón es el enum de Wompi, no el texto libre del failureReason")
        void declinedWebhookUsesBoundedReasonTag() {
            WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                    .status(WompiTransactionStatus.DECLINED).build();
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial(1L, "Tienda X"))
                    .netAmountCents(90_000L).wompiTransaction(tx).status(PayoutStatus.PROCESSING).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(payoutRepository.findByWompiTransactionId(tx.getId())).thenReturn(Optional.of(payout));

            service.handleWompiResult(tx.getId());

            assertThat(counter("payout.confirmed", "outcome", "FAILED", "reason", "DECLINED")).isEqualTo(1d);
            assertThat(counter("payout.paid.amount.cents")).isZero();
        }

        @Test
        @DisplayName("los gauges vuelven a cero cuando el estado deja de aparecer en el agregado")
        void gaugesResetWhenStatusDisappears() {
            ZonedDateTime hace3Dias = ZonedDateTime.now(ZoneOffset.UTC).minusDays(3);
            when(payoutRepository.aggregateByStatus(any()))
                    .thenReturn(List.of(aggregate(PayoutStatus.PROCESSING, 2L, 180_000L, hace3Dias)))
                    .thenReturn(List.of());

            payoutMetrics.refreshPayoutGauges();

            assertThat(gauge("payout.pending.count", "PROCESSING")).isEqualTo(2d);
            assertThat(gauge("payout.pending.amount.cents", "PROCESSING")).isEqualTo(180_000d);
            // ~3 días: es el payout cuyo webhook nunca llegó.
            assertThat(gauge("payout.oldest.age", "PROCESSING")).isGreaterThan(3 * 24 * 3600 - 60d);
            // Un estado sin filas debe leerse 0, no quedar sin serie.
            assertThat(gauge("payout.pending.count", "FAILED")).isZero();

            // Segundo refresco sin filas: si no se limpiara, la alerta quedaría disparada
            // para siempre después de resolver el atasco.
            payoutMetrics.refreshPayoutGauges();

            assertThat(gauge("payout.pending.count", "PROCESSING")).isZero();
            assertThat(gauge("payout.oldest.age", "PROCESSING")).isZero();
        }

        @Test
        @DisplayName("el balance de Wompi es NaN hasta la primera lectura exitosa, no cero")
        void balanceIsNaNUntilFirstRead() {
            assertThat(meterRegistry.get("wompi.payout.balance.cents").gauge().value()).isNaN();

            payoutMetrics.wompiBalanceRead(7_500_000L);

            assertThat(meterRegistry.get("wompi.payout.balance.cents").gauge().value()).isEqualTo(7_500_000d);
            assertThat(meterRegistry.get("wompi.payout.balance.age").gauge().value()).isLessThan(5d);
        }

        private double gauge(String name, String status) {
            return meterRegistry.get(name).tag("status", status).gauge().value();
        }

        private PayoutRepository.PayoutStatusAggregate aggregate(
                PayoutStatus status, long total, long netCents, ZonedDateTime oldest) {
            return new PayoutRepository.PayoutStatusAggregate() {
                @Override public PayoutStatus getStatus() { return status; }
                @Override public long getTotal() { return total; }
                @Override public long getNetCents() { return netCents; }
                @Override public ZonedDateTime getOldestScheduledAt() { return oldest; }
            };
        }
    }
}
