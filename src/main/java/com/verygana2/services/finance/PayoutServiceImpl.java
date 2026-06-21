package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutItem;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutItemRepository payoutItemRepository;
    private final CopaymentRepository copaymentRepository;
    private final TreasuryService treasuryService;

    @Override
    public BigDecimal getCommercialEarningsForPeriod(Long commercialId, Integer year, Integer month) {
        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime endDate = startDate.plusMonths(1);
        return payoutRepository.sumTotalByCommercialIdAndPeriod(commercialId, startDate, endDate);
    }

    /**
     * Fase 1 del job diario: agrupa copayments COMPLETED del período y crea un
     * Payout por empresario.
     *
     * La comisión ya fue retenida en handleApproved() al momento de la venta,
     * por lo que este método NO llama a retainCommission(). Solo registra el
     * snapshot de lo que fue cobrado (commissionCents) para auditoría del payout.
     */
    @Override
    @Transactional
    public void scheduleDailyPayouts(ZonedDateTime periodStart, ZonedDateTime periodEnd) {
        log.info("[PAYOUT-SCHEDULER] Buscando copayments COMPLETED entre {} y {}", periodStart, periodEnd);

        List<Copayment> completed = copaymentRepository.findCompletedInPeriod(
                CopaymentStatus.COMPLETED, periodStart, periodEnd);

        if (completed.isEmpty()) {
            log.info("[PAYOUT-SCHEDULER] Sin copayments COMPLETED en el período. Nada que pagar.");
            return;
        }

        log.info("[PAYOUT-SCHEDULER] Encontrados {} copayments COMPLETED.", completed.size());

        // Agrupar por commercial: commercialId → acumuladores por copayment
        // Estructura: commercialId → Map<copaymentId, {gross, commission, net, commercial, copayment}>
        Map<Long, CommercialGroup> groups = new HashMap<>();

        for (Copayment copayment : completed) {
            for (PurchaseItem item : copayment.getPurchase().getItems()) {
                CommercialDetails commercial = item.getProduct().getCommercial();
                Long commercialId = commercial.getId();

                // Idempotencia: si ya existe un PayoutItem para (copayment, commercial), ignorar
                if (payoutItemRepository.existsByCopaymentAndCommercial(copayment.getId(), commercialId)) {
                    log.debug("[PAYOUT-SCHEDULER] Copayment {} ya pagado para commercial {}. Saltando.",
                            copayment.getId(), commercialId);
                    continue;
                }

                CommercialGroup group = groups.computeIfAbsent(commercialId,
                        k -> new CommercialGroup(commercial));

                group.addItem(copayment, item);
            }
        }

        if (groups.isEmpty()) {
            log.info("[PAYOUT-SCHEDULER] Todos los copayments ya tienen PayoutItems. Nada nuevo.");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        for (CommercialGroup group : groups.values()) {
            CommercialDetails commercial = group.commercial;

            if (!commercial.canReceivePayouts()) {
                log.warn("[PAYOUT-SCHEDULER] Empresario {} ({}) no tiene método de pago verificado. " +
                        "Payout SCHEDULED sin transferencia posible.",
                        commercial.getId(), commercial.getCompanyName());
            }

            Payout payout = Payout.builder()
                    .commercial(commercial)
                    .grossAmountCents(group.totalGrossCents)
                    .commissionCents(group.totalCommissionCents)
                    .netAmountCents(group.totalNetCents)
                    .commissionPctApplied(group.commissionPctSnapshot)
                    .status(PayoutStatus.SCHEDULED)
                    .scheduledAt(now)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();

            payout = payoutRepository.save(payout);

            for (Map.Entry<Copayment, Long> entry : group.copaymentAmounts.entrySet()) {
                PayoutItem item = PayoutItem.builder()
                        .payout(payout)
                        .copayment(entry.getKey())
                        .amountCents(entry.getValue())
                        .build();
                payoutItemRepository.save(item);
            }

            log.info("[PAYOUT-SCHEDULER] Payout SCHEDULED: id={}, commercial={}, net={}",
                    payout.getId(), commercial.getCompanyName(), group.totalNetCents);
        }
    }

    /**
     * Fase 2 del job diario: ejecuta las transferencias Wompi para todos los
     * payouts en estado SCHEDULED.
     *
     * NOTA: La integración con Wompi Transfer API está pendiente de implementación.
     * Por ahora marca el payout como PAID directamente para completar el flujo.
     * Al integrar Wompi: crear WompiTransaction, pasar el payout a PROCESSING,
     * y dejar que el webhook de Wompi lo marque como PAID.
     */
    @Override
    @Transactional
    public void processScheduledPayouts() {
        List<Payout> scheduled = payoutRepository.findByStatus(PayoutStatus.SCHEDULED);

        if (scheduled.isEmpty()) {
            log.info("[PAYOUT-SCHEDULER] Sin payouts SCHEDULED para procesar.");
            return;
        }           

        log.info("[PAYOUT-SCHEDULER] Procesando {} payouts SCHEDULED.", scheduled.size());

        for (Payout payout : scheduled) {
            try {
                processOnePayout(payout);
            } catch (Exception e) {
                log.error("[PAYOUT-SCHEDULER] Error procesando payout {}: {}",
                        payout.getId(), e.getMessage(), e);
                payout.setStatus(PayoutStatus.FAILED);
                payout.setFailureReason(e.getMessage());
                payoutRepository.save(payout);
            }
        }
    }

    /**
     * Reintenta los payouts FAILED del ciclo anterior.
     * El PayoutScheduler lo llama con el rango del día anterior.
     */
    @Override
    @Transactional
    public void retryFailedPayouts(ZonedDateTime previousPeriodStart, ZonedDateTime previousPeriodEnd) {
        List<Payout> failed = payoutRepository.findFailedForRetry(
                PayoutStatus.FAILED, previousPeriodStart, previousPeriodEnd);

        if (failed.isEmpty()) {
            log.info("[PAYOUT-RETRY] Sin payouts FAILED para reintentar.");
            return;
        }

        log.info("[PAYOUT-RETRY] Reintentando {} payouts FAILED.", failed.size());

        for (Payout payout : failed) {
            payout.setRetryCount(payout.getRetryCount() + 1);
            payout.setStatus(PayoutStatus.SCHEDULED);
            payout.setFailureReason(null);
            payoutRepository.save(payout);

            try {
                processOnePayout(payout);
            } catch (Exception e) {
                log.error("[PAYOUT-RETRY] Reintento fallido para payout {}: {}",
                        payout.getId(), e.getMessage(), e);
                payout.setStatus(PayoutStatus.FAILED);
                payout.setFailureReason(e.getMessage());
                payoutRepository.save(payout);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponseDTO> getPayoutsForDate(LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime end = start.plusDays(1);

        return payoutRepository.findByScheduledAtBetweenOrderByScheduledAtDesc(start, end)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // ─── Privados ─────────────────────────────────────────────────────────────

    private void processOnePayout(Payout payout) {
        CommercialDetails commercial = payout.getCommercial();

        if (!commercial.canReceivePayouts()) {
            throw new IllegalStateException(
                    "Empresario " + commercial.getId() + " no tiene método de pago verificado.");
        }

        // TODO: Integrar Wompi Transfer API cuando esté disponible.
        // Flujo futuro:
        //   WompiTransaction tx = wompiService.initiateTransfer(payout);
        //   payout.setWompiTransaction(tx);
        //   payout.setStatus(PayoutStatus.PROCESSING);
        // El webhook de Wompi completará el payout.
        //
        // Por ahora: marcar como PAID directamente y registrar el movimiento.

        treasuryService.registerPayoutSent(payout.getNetAmountCents(), payout.getId());

        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(ZonedDateTime.now(ZoneOffset.UTC));
        payoutRepository.save(payout);

        log.info("[PAYOUT-SCHEDULER] Payout PAID: id={}, commercial={}, net={}",
                payout.getId(), commercial.getCompanyName(), payout.getNetAmountCents());
    }

    private PayoutResponseDTO toResponseDTO(Payout payout) {
        // contar PayoutItems asociados
        // Se usa size de la colección lazy; si hay N+1 aquí,
        // se puede optimizar con un @Query COUNT en el futuro.
        return new PayoutResponseDTO(
                payout.getId(),
                payout.getCommercial().getId(),
                payout.getCommercial().getCompanyName(),
                payout.getGrossAmountCents(),
                payout.getCommissionCents(),
                payout.getNetAmountCents(),
                payout.getCommissionPctApplied(),
                0, // copaymentCount: se puede enriquecer con un COUNT query si se necesita
                payout.getStatus(),
                payout.getScheduledAt(),
                payout.getPaidAt(),
                payout.getPeriodStart(),
                payout.getPeriodEnd(),
                payout.getFailureReason(),
                payout.getRetryCount());
    }

    // ─── Clase auxiliar de agrupación (sólo usada dentro del job) ─────────────

    private static class CommercialGroup {
        final CommercialDetails commercial;
        final Map<Copayment, Long> copaymentAmounts = new HashMap<>();
        long totalGrossCents = 0;
        long totalCommissionCents = 0;
        long totalNetCents = 0;
        int commissionPctSnapshot = 0;

        CommercialGroup(CommercialDetails commercial) {
            this.commercial = commercial;
        }

        void addItem(Copayment copayment, PurchaseItem item) {
            copaymentAmounts.merge(copayment, item.getNetToCommercialCents(), (a, b) -> a + b);
            totalGrossCents += item.getSubtotalCents();
            totalCommissionCents += item.getCommissionCents();
            totalNetCents += item.getNetToCommercialCents();
            // Usar el último pct aplicado como snapshot (todos los ítems del mismo
            // comercial en el mismo plan tienen el mismo pct)
            commissionPctSnapshot = item.getCommissionPctApplied();
        }
    }
}
