package com.verygana2.services.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.prosperity.ProsperityAdjustmentRequestDTO;
import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityReconciliationResultDTO;
import com.verygana2.dtos.prosperity.ProsperityReversalRequestDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityThresholdResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.mappers.finance.ProsperityMapper;
import com.verygana2.models.enums.finance.ProsperityEntryType;
import com.verygana2.models.enums.finance.ProsperityOriginType;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.prosperity.ProsperityAccount;
import com.verygana2.models.finance.prosperity.ProsperityLedgerEntry;
import com.verygana2.models.finance.prosperity.ProsperityThreshold;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityAccountRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityLedgerEntryRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityThresholdRepository;
import com.verygana2.services.interfaces.finance.ProsperityService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Motor de Prosperidad (MP-05). Reglas del Contrato B:
 * <ul>
 *   <li>UMBRAL_GENERADO = INVERSIÓN_COMPUTABLE (neto sin IVA) × multiplicador del plan (5.7, 10.3).</li>
 *   <li>SALDO_NUEVO = SALDO_ANTERIOR + UMBRAL_GENERADO: acumulativo, nunca se reinicia (10.4).</li>
 *   <li>PORCIÓN_ABSORBIDA = MIN(SALDO, VENTA_COMPUTABLE); BASE_COMISIONABLE = VENTA − ABSORBIDA (10.6, 10.10, 10.13).</li>
 *   <li>Toda corrección es un asiento compensatorio; el libro nunca se edita (10.27).</li>
 * </ul>
 * Toda escritura bloquea la cuenta del comercial (PESSIMISTIC_WRITE): no hay doble
 * consumo ni saldos leídos a destiempo entre ventas, inversiones y reembolsos concurrentes.
 * Como cada operación se aplica en el instante en que se valida, una inversión nunca
 * absorbe ventas anteriores a ella (prohibición de alterar el orden cronológico, 10.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProsperityServiceImpl implements ProsperityService {

    static final String MULTIPLIER_FEATURE = "PROSPERITY_THRESHOLD_MULTIPLIER";

    static final String DISCLAIMER = "El Saldo de Prosperidad no es dinero, depósito, ahorro, crédito ni saldo "
            + "retirable. Es la porción de tu Umbral que todavía puede absorber ventas antes de que se "
            + "cause comisión.";

    private static final String BY_INVESTMENT = "SYSTEM:INVESTMENT_CONFIRMATION";
    private static final String BY_COPAYMENT = "SYSTEM:COPAYMENT_APPROVAL";
    private static final String BY_REFUND = "SYSTEM:PURCHASE_ITEM_REFUND";

    private static final EnumSet<ProsperityEntryType> CREDIT_TYPES = EnumSet.of(
            ProsperityEntryType.THRESHOLD_GENERATED,
            ProsperityEntryType.REFUND_REINTEGRATION,
            ProsperityEntryType.ADJUSTMENT_CREDIT);

    private final ProsperityAccountRepository accountRepository;
    private final ProsperityThresholdRepository thresholdRepository;
    private final ProsperityLedgerEntryRepository ledgerRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final TreasuryConfig treasuryConfig;
    private final ProsperityMapper prosperityMapper;

    // ─── Umbral ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Optional<ProsperityThreshold> generateThreshold(Investment investment) {
        if (!Boolean.TRUE.equals(investment.getConfirmed()) || investment.getConfirmedAt() == null) {
            throw new IllegalStateException("Solo una inversión confirmada genera Umbral: investmentId=" + investment.getId());
        }

        int multiplier = investment.getPlanAtDeposit().getIntFeature(MULTIPLIER_FEATURE, 0);
        if (multiplier <= 0) {
            return Optional.empty();
        }

        Optional<ProsperityThreshold> existing = thresholdRepository.findByInvestmentId(investment.getId());
        if (existing.isPresent()) {
            log.info("[PROSPERITY] Umbral ya generado para investmentId={} — evento duplicado ignorado",
                    investment.getId());
            return existing;
        }

        CommercialDetails commercial = investment.getWallet().getCommercial();
        ProsperityAccount account = lockOrOpenAccount(commercial);

        ProsperityThreshold threshold = thresholdRepository.save(
                ProsperityThreshold.of(account, investment, multiplier, investment.getConfirmedAt()));

        append(account, ProsperityLedgerEntry.Draft.builder()
                .type(ProsperityEntryType.THRESHOLD_GENERATED)
                .amountCents(threshold.getGeneratedCents())
                .originType(ProsperityOriginType.INVESTMENT)
                .originId(String.valueOf(investment.getId()))
                .threshold(threshold)
                .idempotencyKey("THRESHOLD:INV:" + investment.getId())
                .effectiveAt(investment.getConfirmedAt())
                .performedBy(BY_INVESTMENT)
                .build());

        log.info("[PROSPERITY] Umbral generado: commercialId={}, investmentId={}, neto={}, x{} = {}, saldo={}",
                commercial.getId(), investment.getId(), threshold.getInvestmentNetCents(), multiplier,
                threshold.getGeneratedCents(), account.getBalanceCents());
        return Optional.of(threshold);
    }

    // ─── Absorción de ventas ──────────────────────────────────────────────────

    @Override
    @Transactional
    public void absorbPurchase(Purchase purchase) {
        int vatPct = treasuryConfig.getVatPct();

        // TreeMap: las cuentas se bloquean siempre en orden de commercialId, así dos
        // compras concurrentes con los mismos comerciales no se bloquean en cruz.
        Map<Long, List<PurchaseItem>> itemsByCommercial = new TreeMap<>();
        for (PurchaseItem item : purchase.getItems()) {
            itemsByCommercial.computeIfAbsent(item.getCommercialId(), k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<Long, List<PurchaseItem>> group : itemsByCommercial.entrySet()) {
            Optional<ProsperityAccount> accountOpt = group.getKey() == null
                    ? Optional.empty()
                    : accountRepository.findByCommercialIdForUpdate(group.getKey());
            boolean canAbsorb = accountOpt.isPresent() && isStandard(accountOpt.get().getCommercial());

            for (PurchaseItem item : group.getValue()) {
                long absorbed = canAbsorb ? absorbItem(accountOpt.get(), item) : 0L;
                item.settleCommission(absorbed, vatPct);
            }
        }

        purchase.calculateFinancials();
    }

    private long absorbItem(ProsperityAccount account, PurchaseItem item) {
        String key = "ABSORPTION:ITEM:" + item.getId();
        Optional<ProsperityLedgerEntry> previous = ledgerRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return previous.get().getAmountCents();
        }

        long absorbed = Math.min(account.getBalanceCents(), item.getSubtotalCents());
        if (absorbed <= 0) {
            return 0L;
        }

        append(account, ProsperityLedgerEntry.Draft.builder()
                .type(ProsperityEntryType.SALE_ABSORPTION)
                .amountCents(absorbed)
                .originType(ProsperityOriginType.PURCHASE_ITEM)
                .originId(String.valueOf(item.getId()))
                .saleAmountCents(item.getSubtotalCents())
                .idempotencyKey(key)
                .effectiveAt(ZonedDateTime.now(ZoneOffset.UTC))
                .performedBy(BY_COPAYMENT)
                .build());

        log.info("[PROSPERITY] Venta absorbida: commercialId={}, itemId={}, venta={}, absorbido={}, base={}, saldo={}",
                item.getCommercialId(), item.getId(), item.getSubtotalCents(), absorbed,
                item.getSubtotalCents() - absorbed, account.getBalanceCents());
        return absorbed;
    }

    @Override
    @Transactional
    public void reintegrateRefund(PurchaseItem item) {
        long absorbed = item.getProsperityAbsorbedCents() == null ? 0L : item.getProsperityAbsorbedCents();
        if (absorbed <= 0) {
            return;
        }

        ProsperityAccount account = accountRepository.findByCommercialIdForUpdate(item.getCommercialId())
                .orElseThrow(() -> new IllegalStateException(
                        "[PROSPERITY] Ítem con absorción sin cuenta de prosperidad: itemId=" + item.getId()));

        // Se reintegra aunque la cuenta esté congelada: el Saldo se conserva para cuando
        // el comercial vuelva a STANDARD.
        append(account, ProsperityLedgerEntry.Draft.builder()
                .type(ProsperityEntryType.REFUND_REINTEGRATION)
                .amountCents(absorbed)
                .originType(ProsperityOriginType.PURCHASE_ITEM)
                .originId(String.valueOf(item.getId()))
                .relatedEntry(ledgerRepository.findByIdempotencyKey("ABSORPTION:ITEM:" + item.getId()).orElse(null))
                .idempotencyKey("REINTEGRATION:ITEM:" + item.getId())
                .effectiveAt(ZonedDateTime.now(ZoneOffset.UTC))
                .performedBy(BY_REFUND)
                .build());

        log.info("[PROSPERITY] Reintegro por reembolso: commercialId={}, itemId={}, reintegrado={}, saldo={}",
                item.getCommercialId(), item.getId(), absorbed, account.getBalanceCents());
    }

    // ─── Operaciones administrativas ──────────────────────────────────────────

    @Override
    @Transactional
    public ProsperityMovementResponseDTO reverseThreshold(Long investmentId, ProsperityReversalRequestDTO request,
            Long adminId) {
        requireCause(request.cause());

        ProsperityThreshold threshold = thresholdRepository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "La inversión " + investmentId + " no tiene Umbral de Prosperidad"));

        // Se bloquea por id de cuenta: navegar threshold.account.commercial cargaría la cuenta
        // sin lock, y si otra transacción la modifica mientras se espera el bloqueo, Hibernate
        // rechaza la versión obsoleta en vez de dejar llegar a la verificación de abajo.
        ProsperityAccount account = accountRepository
                .findByIdForUpdate(threshold.getAccount().getId())
                .orElseThrow();

        // Un Umbral se reversa una sola vez. No se devuelve el asiento previo como si fuera
        // un reintento: la causal y el soporte de esta solicitud no quedarían registrados.
        // Se comprueba con la cuenta ya bloqueada para que dos solicitudes simultáneas no
        // pasen ambas la verificación.
        String key = "REVERSAL:INV:" + investmentId;
        ledgerRepository.findByIdempotencyKey(key).ifPresent(previous -> {
            throw alreadyReversed(investmentId, previous.getEffectiveAt());
        });

        // Sin FIFO entre Umbrales (Guía 5.8) no hay forma de saber qué parte de ESTE Umbral
        // se consumió: se retira lo que el Saldo agregado permita y el resto queda como
        // uncovered para tratamiento contractual. Nunca se deja el Saldo en negativo ni se
        // reescriben ventas ya comisionadas. El Umbral original usa su multiplicador histórico.
        long generated = threshold.getGeneratedCents();
        long reducible = Math.min(account.getBalanceCents(), generated);
        long uncovered = generated - reducible;

        ProsperityLedgerEntry entry;
        try {
            entry = append(account, ProsperityLedgerEntry.Draft.builder()
                    .type(ProsperityEntryType.THRESHOLD_REVERSAL)
                    .amountCents(reducible)
                    .originType(ProsperityOriginType.INVESTMENT)
                    .originId(String.valueOf(investmentId))
                    .threshold(threshold)
                    .relatedEntry(ledgerRepository.findByIdempotencyKey("THRESHOLD:INV:" + investmentId).orElse(null))
                    .uncoveredCents(uncovered)
                    .idempotencyKey(key)
                    .effectiveAt(ZonedDateTime.now(ZoneOffset.UTC))
                    .cause(request.cause())
                    .supportRef(request.supportRef())
                    .performedBy("ADMIN:" + adminId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // MySQL (REPEATABLE READ): la lectura de arriba usa el snapshot tomado antes de
            // esperar el lock y no ve la reversión que otra transacción acaba de confirmar.
            // La atrapa la clave única de idempotencia.
            throw alreadyReversed(investmentId, null);
        }

        if (uncovered > 0) {
            log.warn("[PROSPERITY] Reversión con Umbral ya consumido: investmentId={}, umbral={}, retirado={}, "
                    + "sin cubrir={} — requiere tratamiento contractual", investmentId, generated, reducible, uncovered);
        }
        return prosperityMapper.toMovementResponseDTO(entry);
    }

    @Override
    @Transactional
    public ProsperityMovementResponseDTO adjust(Long commercialId, ProsperityAdjustmentRequestDTO request,
            Long adminId) {
        requireCause(request.cause());

        ProsperityAccount account = accountRepository.findByCommercialIdForUpdate(commercialId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "El comercial " + commercialId + " no tiene cuenta de prosperidad"));

        ProsperityLedgerEntry related = null;
        if (request.relatedEntryId() != null) {
            related = ledgerRepository.findById(request.relatedEntryId())
                    .filter(e -> e.getAccount().getId().equals(account.getId()))
                    .orElseThrow(() -> new BusinessException(
                            "El asiento relacionado no existe o pertenece a otro comercial"));
        }

        String key = "ADJUSTMENT:" + commercialId + ":"
                + (request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID());

        ProsperityLedgerEntry entry = append(account, ProsperityLedgerEntry.Draft.builder()
                .type(Boolean.TRUE.equals(request.credit())
                        ? ProsperityEntryType.ADJUSTMENT_CREDIT
                        : ProsperityEntryType.ADJUSTMENT_DEBIT)
                .amountCents(request.amountCents())
                .originType(ProsperityOriginType.ADMIN)
                .originId(String.valueOf(adminId))
                .relatedEntry(related)
                .idempotencyKey(key)
                .effectiveAt(ZonedDateTime.now(ZoneOffset.UTC))
                .cause(request.cause())
                .supportRef(request.supportRef())
                .performedBy("ADMIN:" + adminId)
                .build());

        log.info("[PROSPERITY] Ajuste manual: commercialId={}, tipo={}, valor={}, adminId={}, causal={}",
                commercialId, entry.getType(), entry.getAmountCents(), adminId, request.cause());
        return prosperityMapper.toMovementResponseDTO(entry);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProsperitySummaryResponseDTO getSummary(Long commercialId) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Comercial no encontrado: " + commercialId));
        boolean standard = isStandard(commercial);

        Optional<ProsperityAccount> accountOpt = accountRepository.findByCommercialId(commercialId);
        if (accountOpt.isEmpty()) {
            return prosperityMapper.toEmptySummaryResponseDTO(commercial.getUser().getPublicId(),
                    standard ? "ACTIVE" : "NOT_APPLICABLE", DISCLAIMER);
        }

        ProsperityAccount account = accountOpt.get();
        Map<Long, ZonedDateTime> reversedAtByThreshold = ledgerRepository
                .findByAccountIdAndType(account.getId(), ProsperityEntryType.THRESHOLD_REVERSAL).stream()
                .collect(Collectors.toMap(e -> e.getThreshold().getId(), ProsperityLedgerEntry::getEffectiveAt));
        List<ProsperityThresholdResponseDTO> thresholds = thresholdRepository
                .findByAccountIdOrderByValidatedAtAsc(account.getId()).stream()
                .map(t -> prosperityMapper.toThresholdResponseDTO(t, reversedAtByThreshold.get(t.getId())))
                .toList();
        return prosperityMapper.toSummaryResponseDTO(account, commercial.getUser().getPublicId(), standard ? "ACTIVE" : "FROZEN",
                thresholds, DISCLAIMER);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProsperityMovementResponseDTO> getMovements(Long commercialId, Pageable pageable) {
        return accountRepository.findByCommercialId(commercialId)
                .map(account -> ledgerRepository.findByAccountIdOrderBySequenceDesc(account.getId(), pageable)
                        .map(prosperityMapper::toMovementResponseDTO))
                .orElseGet(() -> Page.empty(pageable));
    }

    // ─── Conciliación ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProsperityReconciliationResultDTO reconcile() {
        List<ProsperityAccount> accounts = accountRepository.findAll();
        List<String> discrepancies = new ArrayList<>();

        for (ProsperityAccount account : accounts) {
            Long id = account.getId();
            long balance = account.getBalanceCents();

            long ledgerBalance = ledgerRepository.sumSignedByAccountId(id, CREDIT_TYPES);
            if (ledgerBalance != balance) {
                discrepancies.add(String.format("cuenta %d: saldo %d ≠ Σ libro %d", id, balance, ledgerBalance));
            }

            ledgerRepository.findTopByAccountIdOrderBySequenceDesc(id)
                    .filter(last -> last.getBalanceAfterCents() != balance)
                    .ifPresent(last -> discrepancies.add(String.format(
                            "cuenta %d: saldo %d ≠ saldo después del último asiento %d", id, balance,
                            last.getBalanceAfterCents())));

            long expectedAccumulated = thresholdRepository.sumGeneratedByAccountId(id)
                    - ledgerRepository.sumAmountByAccountIdAndType(id, ProsperityEntryType.THRESHOLD_REVERSAL);
            if (expectedAccumulated != account.getAccumulatedThresholdCents()) {
                discrepancies.add(String.format("cuenta %d: Umbral acumulado %d ≠ Σ Umbrales − reversiones %d",
                        id, account.getAccumulatedThresholdCents(), expectedAccumulated));
            }
        }

        if (discrepancies.isEmpty()) {
            log.info("[PROSPERITY-RECONCILIATION] {} cuentas conciliadas sin descuadres", accounts.size());
        } else {
            discrepancies.forEach(d -> log.error("[PROSPERITY-RECONCILIATION] DESCUADRE: {}", d));
        }
        return new ProsperityReconciliationResultDTO(accounts.size(), discrepancies);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Registra un asiento. Si la clave de idempotencia ya existe devuelve el asiento
     * previo sin volver a aplicarlo (MISMO_EVENTO → MISMO_RESULTADO_LÓGICO, 10.25).
     */
    private ProsperityLedgerEntry append(ProsperityAccount account, ProsperityLedgerEntry.Draft draft) {
        Optional<ProsperityLedgerEntry> previous = ledgerRepository.findByIdempotencyKey(draft.idempotencyKey());
        if (previous.isPresent()) {
            return previous.get();
        }
        ProsperityLedgerEntry entry = ProsperityLedgerEntry.record(account, draft);
        accountRepository.save(account);
        return ledgerRepository.save(entry);
    }

    /**
     * Bloquea la cuenta del comercial o la abre si es su primer Umbral. La apertura se
     * serializa con el lock de la fila del comercial para que dos inversiones confirmadas
     * a la vez no intenten crear dos cuentas.
     */
    private ProsperityAccount lockOrOpenAccount(CommercialDetails commercial) {
        Optional<ProsperityAccount> existing = accountRepository.findByCommercialIdForUpdate(commercial.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        commercialDetailsRepository.findByIdForUpdate(commercial.getId());
        return accountRepository.findByCommercialIdForUpdate(commercial.getId())
                .orElseGet(() -> accountRepository.save(ProsperityAccount.openFor(commercial)));
    }

    private boolean isStandard(CommercialDetails commercial) {
        return commercial.getCurrentPlan() != null && commercial.getCurrentPlan().getCode() == PlanCode.STANDARD;
    }

    private InvalidStatusException alreadyReversed(Long investmentId, ZonedDateTime reversedAt) {
        return new InvalidStatusException("El Umbral de la inversión " + investmentId + " ya fue reversado"
                + (reversedAt != null ? " el " + reversedAt : ""));
    }

    private void requireCause(String cause) {
        if (cause == null || cause.isBlank()) {
            throw new BusinessException("Toda corrección del Saldo de Prosperidad requiere causal");
        }
    }
}
