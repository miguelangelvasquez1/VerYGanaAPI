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
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutBalanceResponseDTO;
import com.verygana2.mappers.PayoutMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.PayoutExecutionService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.wompi.WompiPayoutClient;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private WompiPayoutClient wompiPayoutClient;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private PayoutMapper payoutMapper;
    @Mock private PayoutExecutionService payoutExecutionService;
    @Mock private WompiPayoutConfig wompiPayoutConfig;

    private PayoutServiceImpl service;

    /** IVA colombiano usado en la tarifa de Wompi (ver PayoutServiceImpl.WOMPI_COMMISSION_*). */
    private static final double IVA = 0.19;
    private static final long WOMPI_COMMISSION_AMOUNT_BASE = 184_900L;
    private static final double WOMPI_COMMISSION_PCT = 0.004;

    /** Replica la fórmula de PayoutServiceImpl: fija + % del neto, con IVA sobre la porción variable. */
    private static long expectedWompiFeeCents(long netCents) {
        return WOMPI_COMMISSION_AMOUNT_BASE
                + java.math.BigDecimal.valueOf(netCents)
                        .multiply(java.math.BigDecimal.valueOf(WOMPI_COMMISSION_PCT))
                        .multiply(java.math.BigDecimal.valueOf(1 + IVA))
                        .longValue();
    }

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, payoutItemRepository, purchaseItemRepository,
                treasuryService, wompiPayoutClient, wompiTransactionRepository, payoutMapper,
                payoutExecutionService, wompiPayoutConfig);
        ReflectionTestUtils.setField(service, "iva", IVA);

        // Sin pausa real en tests — solo processScheduledPayouts/retryFailedPayouts la usan,
        // así que se marca lenient para no disparar UnnecessaryStubbingException en el resto.
        WompiPayoutConfig.Payout payoutConfig = new WompiPayoutConfig.Payout();
        payoutConfig.setRateLimitDelayMs(0L);
        lenient().when(wompiPayoutConfig.getPayout()).thenReturn(payoutConfig);
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

    private WompiPayoutBalanceResponseDTO.Account wompiBalance(long balanceInCents) {
        WompiPayoutBalanceResponseDTO.Account account = new WompiPayoutBalanceResponseDTO.Account();
        account.setBalanceInCents(balanceInCents);
        return account;
    }

    private PurchaseItem claimedItem(CommercialDetails commercial, long subtotal, long commission, long net) {
        Product product = new Product();
        product.setCommercial(commercial);
        PurchaseItem item = PurchaseItem.builder().product(product)
                .subtotalCents(subtotal).commissionCents(commission).netToCommercialCents(net)
                .commissionPctApplied(10).status(PurchaseItemStatus.CLAIMED).build();
        Purchase purchase = Purchase.builder().items(new java.util.ArrayList<>(List.of(item))).build();
        item.setPurchase(purchase);
        return item;
    }

    @Nested
    @DisplayName("scheduleDailyPayouts")
    class ScheduleDailyPayouts {

        @Test
        @DisplayName("sin ítems CLAIMED pendientes de payout: no crea ningún payout")
        void noClaimedItems_createsNothing() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
            when(purchaseItemRepository.findClaimedWithoutPayout()).thenReturn(List.of());

            service.scheduleDailyPayouts(start, start.plusDays(1));

            verify(payoutRepository, never()).save(any());
        }

        @Test
        @DisplayName("agrupa por comercial y crea un Payout con sus PayoutItems")
        void groupsByCommercialAndCreatesPayout() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            PurchaseItem item = claimedItem(commercial, 100_000L, 10_000L, 90_000L);

            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
            when(purchaseItemRepository.findClaimedWithoutPayout()).thenReturn(List.of(item));
            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

            service.scheduleDailyPayouts(start, start.plusDays(1));

            var captor = org.mockito.ArgumentCaptor.forClass(Payout.class);
            verify(payoutRepository).save(captor.capture());
            assertThat(captor.getValue().getNetAmountCents()).isEqualTo(90_000L);
            assertThat(captor.getValue().getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

            long expectedFee = expectedWompiFeeCents(90_000L);
            assertThat(captor.getValue().getCommissionAmountCents()).isEqualTo(expectedFee);
            assertThat(captor.getValue().getGrossAmountCents()).isEqualTo(90_000L + expectedFee);

            var itemCaptor = org.mockito.ArgumentCaptor.forClass(com.verygana2.models.finance.PayoutItem.class);
            verify(payoutItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getPurchaseItem()).isEqualTo(item);
            assertThat(itemCaptor.getValue().getAmountCents()).isEqualTo(90_000L);
        }

        @Test
        @DisplayName("dos ítems del mismo comercial: un solo Payout agregando ambos, con un PayoutItem por ítem")
        void twoItemsSameCommercial_oneAggregatedPayoutWithOnePayoutItemEach() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            PurchaseItem item1 = claimedItem(commercial, 100_000L, 10_000L, 90_000L);
            PurchaseItem item2 = claimedItem(commercial, 50_000L, 5_000L, 45_000L);

            when(purchaseItemRepository.findClaimedWithoutPayout()).thenReturn(List.of(item1, item2));
            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

            service.scheduleDailyPayouts(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));

            var captor = org.mockito.ArgumentCaptor.forClass(Payout.class);
            verify(payoutRepository).save(captor.capture());
            assertThat(captor.getValue().getNetAmountCents()).isEqualTo(135_000L);

            long expectedFee = expectedWompiFeeCents(135_000L);
            assertThat(captor.getValue().getCommissionAmountCents()).isEqualTo(expectedFee);
            assertThat(captor.getValue().getGrossAmountCents()).isEqualTo(135_000L + expectedFee);

            verify(payoutItemRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }

    /**
     * La ejecución real de cada payout (llamada a Wompi, actualización de
     * estado) vive en {@link PayoutExecutionService}, en su propia transacción
     * (ver {@link PayoutExecutionServiceImplTest}). Acá solo se prueba la
     * orquestación: que se dispare un envío aislado por cada payout, y que un
     * payout cuya transacción aislada explota (incluso a nivel de
     * persistencia) no detenga el procesamiento de los demás del batch — ese
     * era justamente el riesgo de tener todo el batch en una sola transacción.
     */
    @Nested
    @DisplayName("processScheduledPayouts")
    class ProcessScheduledPayouts {

        @Test
        @DisplayName("sin payouts SCHEDULED: no hace nada")
        void noScheduled_doesNothing() {
            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

            service.processScheduledPayouts();

            verify(payoutExecutionService, never()).executeScheduledPayout(any());
        }

        @Test
        @DisplayName("dispara un envío aislado por cada payout SCHEDULED")
        void triggersIsolatedExecutionPerPayout() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout1 = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();
            Payout payout2 = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(45_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout1, payout2));

            service.processScheduledPayouts();

            verify(payoutExecutionService).executeScheduledPayout(payout1.getId());
            verify(payoutExecutionService).executeScheduledPayout(payout2.getId());
        }

        @Test
        @DisplayName("si la transacción aislada de un payout explota, los demás del batch se siguen procesando")
        void isolatedFailureDoesNotStopTheRestOfTheBatch() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout brokenPayout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();
            Payout okPayout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(45_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(brokenPayout, okPayout));
            doThrow(new RuntimeException("sesión de BD corrompida"))
                    .when(payoutExecutionService).executeScheduledPayout(brokenPayout.getId());

            service.processScheduledPayouts();

            verify(payoutExecutionService).executeScheduledPayout(okPayout.getId());
        }

        @Test
        @DisplayName("balance de Wompi insuficiente para todos: los que no alcanzan se marcan sin llamar a Wompi")
        void insufficientBalance_marksRemainingPayoutsWithoutCallingWompi() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout covered = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(60_000L).status(PayoutStatus.SCHEDULED).build();
            Payout notCovered = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(covered, notCovered));
            when(wompiPayoutClient.getBalance()).thenReturn(wompiBalance(100_000L));
            when(payoutExecutionService.executeScheduledPayout(covered.getId())).thenReturn(PayoutStatus.PROCESSING);

            service.processScheduledPayouts();

            verify(payoutExecutionService).executeScheduledPayout(covered.getId());
            verify(payoutExecutionService, never()).executeScheduledPayout(notCovered.getId());
            // balance restante tras "covered": 100_000 - 60_000 = 40_000, insuficiente para los 90_000 de "notCovered"
            verify(payoutExecutionService).markInsufficientBalance(notCovered.getId(), 90_000L, 40_000L);
        }

        @Test
        @DisplayName("no se puede consultar el balance de Wompi: procesa el batch igual, sin el corte local")
        void balanceUnavailable_processesWithoutLocalCutoff() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(wompiPayoutClient.getBalance()).thenThrow(new RuntimeException("Wompi no disponible"));

            service.processScheduledPayouts();

            verify(payoutExecutionService).executeScheduledPayout(payout.getId());
            verify(payoutExecutionService, never()).markInsufficientBalance(any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("respeta la pausa configurada entre llamadas consecutivas a Wompi (evita 429 por volumen)")
        void pausesBetweenConsecutiveWompiCalls() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout1 = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(60_000L).status(PayoutStatus.SCHEDULED).build();
            Payout payout2 = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(45_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout1, payout2));
            WompiPayoutConfig.Payout payoutConfig = new WompiPayoutConfig.Payout();
            payoutConfig.setRateLimitDelayMs(40L);
            when(wompiPayoutConfig.getPayout()).thenReturn(payoutConfig);

            long start = System.nanoTime();
            service.processScheduledPayouts();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // 2 payouts → al menos 2 pausas de 40ms cada una (se pausa después de cada llamada).
            assertThat(elapsedMs).isGreaterThanOrEqualTo(80L);
        }
    }

    @Test
    @DisplayName("retryFailedPayouts: dispara un reintento aislado por cada payout FAILED")
    void retryFailedPayouts_triggersIsolatedRetryPerPayout() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(0)
                .failureReason("Error previo").build();

        when(payoutRepository.findByStatus(PayoutStatus.FAILED)).thenReturn(List.of(payout));

        service.retryFailedPayouts();

        verify(payoutExecutionService).executeRetry(payout.getId());
    }

    @Test
    @DisplayName("retryFailedPayouts: si el reintento aislado de un payout explota, los demás se siguen reintentando")
    void retryFailedPayouts_isolatedFailureDoesNotStopTheRestOfTheBatch() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        Payout brokenPayout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(0).build();
        Payout okPayout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(45_000L).status(PayoutStatus.FAILED).retryCount(0).build();

        when(payoutRepository.findByStatus(PayoutStatus.FAILED)).thenReturn(List.of(brokenPayout, okPayout));
        doThrow(new RuntimeException("sesión de BD corrompida"))
                .when(payoutExecutionService).executeRetry(brokenPayout.getId());

        service.retryFailedPayouts();

        verify(payoutExecutionService).executeRetry(okPayout.getId());
    }

    @Test
    @DisplayName("retryFailedPayouts: balance de Wompi insuficiente: marca sin llamar a Wompi en vez de reintentar")
    void retryFailedPayouts_insufficientBalance_marksWithoutCallingWompi() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(0).build();

        when(payoutRepository.findByStatus(PayoutStatus.FAILED)).thenReturn(List.of(payout));
        when(wompiPayoutClient.getBalance()).thenReturn(wompiBalance(50_000L));

        service.retryFailedPayouts();

        verify(payoutExecutionService, never()).executeRetry(payout.getId());
        verify(payoutExecutionService).markInsufficientBalance(payout.getId(), 90_000L, 50_000L);
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
}
