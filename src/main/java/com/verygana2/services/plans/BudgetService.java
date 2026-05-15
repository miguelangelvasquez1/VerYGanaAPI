package com.verygana2.services.plans;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.BudgetTransaction.TransactionType;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.plans.BudgetTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestiona el consumo del presupuesto publicitario y su trazabilidad.
 *
 * Usa lock pesimista sobre el Wallet para evitar race conditions cuando
 * múltiples eventos consumen presupuesto de forma concurrente (p. ej.
 * varias impresiones de anuncios en el mismo instante).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final WalletRepository walletRepository;
    private final BudgetTransactionRepository transactionRepository;
    private final InvestmentService investmentService;

    /**
     * @param commercialId ID del comercial anunciante
     * @param amountCents  Costo de la impresión en centavos
     * @param referenceId  ID externo de la impresión (para auditoría)
     */
    @Transactional
    public void consumeForAdView(Long commercialId, Long amountCents, String referenceId) {
        consume(commercialId, amountCents, TransactionType.AD_VIEW, referenceId,
                "Costo por visualización de anuncio");
    }

    /**
     * @param commercialId ID del comercial anunciante
     * @param amountCents  Monto de la recompensa en centavos
     * @param referenceId  ID externo de la sesión de juego (para auditoría)
     */
    @Transactional
    public void consumeForGameReward(Long commercialId, Long amountCents, String referenceId) {
        consume(commercialId, amountCents, TransactionType.GAME_REWARD, referenceId,
                "Recompensa por sesión de juego branded");
    }

    /**
     * @param commercialId ID del comercial
     * @param amountCents  Monto a descontar en centavos
     * @param description  Descripción del ajuste
     */
    @Transactional
    public void applyManualAdjustment(Long commercialId, Long amountCents, String description) {
        consume(commercialId, amountCents, TransactionType.MANUAL_ADJUSTMENT, null, description);
    }

    // ── Implementación interna ────────────────────────────────────────────────

    private void consume(Long commercialId, Long amountCents, TransactionType type,
            String referenceId, String description) {

        Wallet wallet = walletRepository.findByCommercialIdForUpdate(commercialId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Wallet no encontrado para comercial: " + commercialId));

        try {
            wallet.consume(amountCents);
        } catch (InsufficientFundsException e) {
            log.warn("Saldo insuficiente en wallet del comercial {}. Requerido: {} centavos, disponible: {}",
                    commercialId, amountCents, wallet.getBalanceCents());
            throw e;
        }

        walletRepository.save(wallet);

        BudgetTransaction tx = BudgetTransaction.builder()
                .wallet(wallet)
                .amountCents(amountCents)
                .type(type)
                .referenceId(referenceId)
                .description(description)
                .createdAt(ZonedDateTime.now(ZoneOffset.UTC))
                .build();
        transactionRepository.save(tx);

        log.debug("Wallet comercial {} consumido: {} centavos por {}. Saldo restante: {}",
                commercialId, amountCents, type, wallet.getBalanceCents());

        if (wallet.isExhausted()) {
            log.info("Wallet comercial {} agotado. Removiendo plan activo.", commercialId);
            investmentService.handleWalletExhausted(commercialId);
        }
    }
}
