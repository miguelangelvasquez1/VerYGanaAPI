package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.mappers.PayoutMapper;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutItem;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.PayoutExecutionService;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.wompi.WompiPayoutClient;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutItemRepository payoutItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final TreasuryService treasuryService;
    private final WompiPayoutClient wompiPayoutClient;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final PayoutMapper payoutMapper;
    private final PayoutExecutionService payoutExecutionService;
    private final WompiPayoutConfig wompiPayoutConfig;

    /** Tarifa fija que Wompi cobra por ejecutar cada transferencia de payout. */
    private static final Long WOMPI_COMMISSION_AMOUNT_BASE = 184900L;
    /** Porcentaje de la tarifa de Wompi, sobre el neto transferido al comercial. */
    private static final double WOMPI_COMMISSION_PCT = 0.004;

    /** IVA colombiano sobre la porción variable de la tarifa de Wompi (ej. 0.19). */
    @Value("${taxes.iva}")
    private double iva;

    @Override
    public BigDecimal getCommercialEarningsForDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        return payoutRepository.sumTotalByCommercialIdAndPeriod(commercialId, startDate, endDate);
    }

    /**
     * Fase 1 del job diario: agrupa por empresario los ítems CLAIMED que aún
     * no entraron a ningún Payout y crea un Payout por empresario.
     *
     * A diferencia del diseño original (agrupar Copayment COMPLETED por
     * ventana de 24h), la elegibilidad ahora es por ítem reclamado, sin
     * filtro de fecha: un comprador puede reclamar un ítem físico varios días
     * después de la venta, y ese ítem debe entrar al payout del día en que se
     * reclamó, no perderse ni esperar al día de la venta. periodStart/periodEnd
     * ya no filtran nada — solo quedan como metadato de cuándo corrió este batch.
     *
     * La comisión ya fue retenida en handleApproved() al momento de la venta,
     * por lo que este método NO llama a retainCommission(). Solo registra el
     * snapshot de lo que fue cobrado (commissionCents) para auditoría del payout.
     */
    @Override
    @Transactional
    public void scheduleDailyPayouts(ZonedDateTime periodStart, ZonedDateTime periodEnd) {
        log.info("[PAYOUT-SCHEDULER] Buscando ítems CLAIMED sin payout asociado");

        List<PurchaseItem> claimedItems = purchaseItemRepository.findClaimedWithoutPayout();

        if (claimedItems.isEmpty()) {
            log.info("[PAYOUT-SCHEDULER] Sin ítems reclamados pendientes de pago. Nada que pagar.");
            return;
        }

        log.info("[PAYOUT-SCHEDULER] Encontrados {} ítems reclamados sin payout.", claimedItems.size());

        // Agrupar por commercial: commercialId → ítems + acumuladores
        Map<Long, CommercialGroup> groups = new HashMap<>();

        for (PurchaseItem item : claimedItems) {
            CommercialDetails commercial = item.getProduct().getCommercial();
            Long commercialId = commercial.getId();
            
            CommercialGroup group = groups.computeIfAbsent(commercialId,
                    k -> new CommercialGroup(commercial));

            group.addItem(item);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        for (CommercialGroup group : groups.values()) {
            CommercialDetails commercial = group.commercial;

            if (!commercial.canReceivePayouts()) {
                log.warn("[PAYOUT-SCHEDULER] Empresario {} ({}) no tiene método de pago verificado. " +
                        "Payout SCHEDULED sin transferencia posible.",
                        commercial.getId(), commercial.getCompanyName());
            }

            // Tarifa de Wompi por ejecutar la transferencia: fija + % sobre el neto
            // transferido, con IVA sobre la porción variable. Se calcula una sola
            // vez por Payout (Wompi solo ve 1 transacción por comercial por día),
            // no por ítem — de ahí que viva acá y no dentro de CommercialGroup.
            long wompiFeeCents = WOMPI_COMMISSION_AMOUNT_BASE
                    + BigDecimal.valueOf(group.totalNetCents)
                            .multiply(BigDecimal.valueOf(WOMPI_COMMISSION_PCT))
                            .multiply(BigDecimal.valueOf(1 + iva))
                            .longValue();

            Payout payout = Payout.builder()
                    .commercial(commercial)
                    .grossAmountCents(group.totalNetCents + wompiFeeCents)
                    .commissionAmountCents(wompiFeeCents)
                    .netAmountCents(group.totalNetCents)
                    .commissionPctApplied(group.commissionPctSnapshot)
                    .status(PayoutStatus.SCHEDULED)
                    .scheduledAt(now)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();

            payout = payoutRepository.save(payout);

            for (PurchaseItem item : group.items) {
                PayoutItem payoutItem = PayoutItem.builder()
                        .payout(payout)
                        .purchaseItem(item)
                        .amountCents(item.getNetToCommercialCents())
                        .build();
                payoutItemRepository.save(payoutItem);
            }

            log.info("[PAYOUT-SCHEDULER] Payout SCHEDULED: id={}, commercial={}, net={}, ítems={}",
                    payout.getId(), commercial.getCompanyName(), group.totalNetCents, group.items.size());
        }
    }

    /**
     * Fase 2 del job diario: ejecuta las transferencias Wompi para todos los
     * payouts SCHEDULED.
     *
     * A propósito NO es @Transactional: cada payout se envía a Wompi y se
     * persiste en su propia transacción (ver {@link PayoutExecutionService}),
     * aislada del resto del batch. Si el método completo corriera en una sola
     * transacción, un fallo a mitad del lote (ej. la sesión de Hibernate
     * corrompiéndose en el payout #47) podría dejarla rollback-only y revertir
     * en BD los payouts #1-#46 aunque el dinero ya hubiera salido por Wompi.
     */
    @Override
    public void processScheduledPayouts() {
        List<Payout> scheduled = payoutRepository.findByStatus(PayoutStatus.SCHEDULED);

        if (scheduled.isEmpty()) {
            log.info("[PAYOUT-SCHEDULER] Sin payouts SCHEDULED para procesar.");
            return;
        }

        log.info("[PAYOUT-SCHEDULER] Procesando {} payouts SCHEDULED.", scheduled.size());

        Long remainingBalanceCents = fetchWompiBalanceCentsOrNull();

        for (Payout payout : scheduled) {
            if (skipIfInsufficientBalance(payout, remainingBalanceCents)) continue;

            UUID payoutId = payout.getId();
            try {
                PayoutStatus result = payoutExecutionService.executeScheduledPayout(payoutId);
                remainingBalanceCents = debit(remainingBalanceCents, payout, result);
            } catch (Exception e) {
                // La transacción aislada de este payout no logró ni siquiera
                // confirmarse como FAILED (ej. sesión de BD corrompida) — se
                // deja para el próximo ciclo en vez de abortar el resto del lote.
                log.error("[PAYOUT-SCHEDULER] Fallo irrecuperable procesando payout {}: {}",
                        payoutId, e.getMessage(), e);
            }

            if (!pauseBetweenWompiCalls()) break;
        }
    }

    /**
     * Reintenta todos los payouts actualmente FAILED, sin filtrar por fecha.
     *
     * Antes filtraba por "el rango del día anterior" calculado igual que
     * scheduleDailyPayouts(), pero ese rango es [ayer 00:00, hoy 00:00) — un
     * Payout que falla hoy a las 11 PM (scheduledAt = hoy) queda FUERA de ese
     * rango cuando este job corre 30 min después (todavía "hoy"), así que nunca
     * se reintentaba la misma noche: recién entraba al rango la noche
     * siguiente. Al no filtrar por fecha, cualquier FAILED se reintenta en el
     * primer ciclo de reintento disponible, sin depender de en qué día cayó.
     */
    @Override
    public void retryFailedPayouts() {
        List<Payout> failed = payoutRepository.findByStatus(PayoutStatus.FAILED);

        if (failed.isEmpty()) {
            log.info("[PAYOUT-RETRY] Sin payouts FAILED para reintentar.");
            return;
        }

        log.info("[PAYOUT-RETRY] Reintentando {} payouts FAILED.", failed.size());

        Long remainingBalanceCents = fetchWompiBalanceCentsOrNull();

        for (Payout payout : failed) {
            if (skipIfInsufficientBalance(payout, remainingBalanceCents)) continue;

            UUID payoutId = payout.getId();
            try {
                PayoutStatus result = payoutExecutionService.executeRetry(payoutId);
                remainingBalanceCents = debit(remainingBalanceCents, payout, result);
            } catch (Exception e) {
                log.error("[PAYOUT-RETRY] Fallo irrecuperable reintentando payout {}: {}",
                        payoutId, e.getMessage(), e);
            }

            if (!pauseBetweenWompiCalls()) break;
        }
    }

    /**
     * Consulta el balance de la cuenta de dispersión una sola vez al inicio del
     * batch. Si no se puede consultar, se degrada a {@code null} (sin chequeo
     * local de saldo) en vez de bloquear el batch entero — el mismo criterio
     * conservador que ya usaba {@code PayoutScheduler.checkWompiBalance()}.
     */
    private Long fetchWompiBalanceCentsOrNull() {
        try {
            return wompiPayoutClient.getBalance().getBalanceInCents();
        } catch (Exception e) {
            log.warn("[PAYOUT-SCHEDULER] No se pudo consultar el balance de Wompi Payouts para el corte "
                    + "por saldo insuficiente: {}. Se procesará sin ese chequeo local.", e.getMessage());
            return null;
        }
    }

    /**
     * Si ya sabemos (por el balance corriendo local) que este payout no
     * alcanza a cubrirse, lo marca FAILED sin llamar a Wompi — en vez de
     * esperar a que Wompi lo rechace uno por uno con un motivo genérico a
     * mitad del batch. remainingBalanceCents null significa "no se pudo
     * determinar el balance": en ese caso no se corta nada, igual que antes.
     */
    private boolean skipIfInsufficientBalance(Payout payout, Long remainingBalanceCents) {
        if (remainingBalanceCents == null || payout.getNetAmountCents() <= remainingBalanceCents) {
            return false;
        }

        UUID payoutId = payout.getId();
        try {
            payoutExecutionService.markInsufficientBalance(payoutId, payout.getNetAmountCents(), remainingBalanceCents);
        } catch (Exception e) {
            log.error("[PAYOUT-SCHEDULER] Fallo irrecuperable marcando balance insuficiente para payout {}: {}",
                    payoutId, e.getMessage(), e);
        }
        return true;
    }

    /** Descuenta del balance corriente solo si el payout realmente movió dinero (quedó PROCESSING). */
    private Long debit(Long remainingBalanceCents, Payout payout, PayoutStatus result) {
        if (remainingBalanceCents == null || result != PayoutStatus.PROCESSING) {
            return remainingBalanceCents;
        }
        return remainingBalanceCents - payout.getNetAmountCents();
    }

    /**
     * Pausa entre llamadas consecutivas a Wompi dentro del batch. Sin esto,
     * ~100 POST /payouts seguidos sin ninguna pausa pueden empezar a recibir
     * 429 a mitad de la corrida si Wompi tiene un límite de requests por
     * segundo/minuto. Devuelve false si el hilo fue interrumpido (ej. shutdown
     * de la app), para que el loop que llama corte en vez de seguir intentando.
     */
    private boolean pauseBetweenWompiCalls() {
        long delayMs = wompiPayoutConfig.getPayout().getRateLimitDelayMs();
        if (delayMs <= 0) return true;

        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[PAYOUT-SCHEDULER] Pausa entre llamadas a Wompi interrumpida — se corta el batch.");
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponseDTO> getPayoutsForDate(LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime end = start.plusDays(1);

        return payoutRepository.findByScheduledAtBetweenOrderByScheduledAtDesc(start, end)
                .stream()
                .map(payoutMapper::toPayoutResponseDTO)
                .toList();
    }

    /**
     * Punto de entrada del webhook de Wompi para TRANSFER_PAYOUT.
     * El WompiTransaction ya fue actualizado con el estado final por
     * WompiPayoutWebhookController antes de llamar este método.
     */
    @Override
    @Transactional
    public void handleWompiResult(UUID wompiTransactionId) {
        Objects.requireNonNull(wompiTransactionId, "wompiTransactionId no puede ser null");
        log.info("[PAYOUT] Procesando webhook: wompiTxId={}", wompiTransactionId);

        WompiTransaction wompiTx = wompiTransactionRepository.findById(wompiTransactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "[PAYOUT] WompiTransaction no encontrada: " + wompiTransactionId));

        Payout payout = payoutRepository.findByWompiTransactionId(wompiTx.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payout no encontrado para wompiTransactionId=" + wompiTx.getId()));

        // Idempotencia: ignorar si ya se resolvió (solo aplicamos el resultado una vez)
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            log.info("[PAYOUT] Evento duplicado ignorado: payoutId={}, status={}",
                    payout.getId(), payout.getStatus());
            return;
        }

        if (wompiTx.getStatus() == WompiTransactionStatus.APPROVED) {
            treasuryService.registerPayoutSent(payout.getNetAmountCents(), payout.getId());
            payout.setStatus(PayoutStatus.PAID);
            payout.setPaidAt(ZonedDateTime.now(ZoneOffset.UTC));
            log.info("[PAYOUT] Payout PAID: id={}, commercial={}, net={}",
                    payout.getId(), payout.getCommercial().getCompanyName(), payout.getNetAmountCents());
        } else {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(wompiTx.getStatus().name());
            log.warn("[PAYOUT] Payout FAILED: id={}, status={}", payout.getId(), wompiTx.getStatus());
        }

        payoutRepository.save(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getWompiStatus(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Payout no encontrado: " + payoutId));

        if (payout.getWompiTransaction() == null) {
            throw new IllegalStateException(
                    "Payout " + payoutId + " todavía no se envió a Wompi (sin WompiTransaction asociada).");
        }

        return wompiPayoutClient.getPayoutStatus(payout.getWompiTransaction().getWompiId());
    }

    // ─── Clase auxiliar de agrupación (sólo usada dentro del job) ─────────────

    private static class CommercialGroup {
        final CommercialDetails commercial;
        final List<PurchaseItem> items = new ArrayList<>();
        long totalNetCents = 0;
        int commissionPctSnapshot = 0;

        CommercialGroup(CommercialDetails commercial) {
            this.commercial = commercial;
        }

        void addItem(PurchaseItem item) {
            items.add(item);
            totalNetCents += item.getNetToCommercialCents();
            // Usar el último pct aplicado como snapshot (todos los ítems del mismo
            // comercial en el mismo plan tienen el mismo pct)
            commissionPctSnapshot = item.getCommissionPctApplied();
        }
    }
}
