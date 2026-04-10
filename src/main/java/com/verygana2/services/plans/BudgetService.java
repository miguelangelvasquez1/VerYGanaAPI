package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.plans.BudgetTransaction;
import com.verygana2.models.plans.BudgetTransaction.TransactionType;
import com.verygana2.models.plans.Investment;
import com.verygana2.models.plans.Investment.InvestmentStatus;
import com.verygana2.repositories.plans.BudgetTransactionRepository;
import com.verygana2.repositories.plans.InvestmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestiona el consumo del presupuesto y su trazabilidad.
 *
 * Cada interacción de usuario (ver un anuncio, completar un juego) debe
 * invocar este servicio para descontar el costo del Budget y registrar
 * la transacción correspondiente.
 *
 * Cuando el Budget se agota, notifica a InvestmentService para actualizar
 * el estado de la inversión y el anunciante pasa a modo BASIC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final InvestmentRepository budgetRepository;
    private final BudgetTransactionRepository transactionRepository;
    private final InvestmentService investmentService;

    /**
     * Registra el consumo del presupuesto por una visualización de anuncio.
     *
     * @param budgetId    ID del presupuesto activo
     * @param cost        Costo de la impresión
     * @param referenceId ID externo de la impresión (para auditoría)
     */
    @Transactional
    public void consumeForAdView(Long budgetId, BigDecimal cost, String referenceId) {
        consume(budgetId, cost, TransactionType.AD_VIEW, referenceId,
                "Costo por visualización de anuncio");
    }

    /**
     * Registra el consumo del presupuesto por una recompensa de juego.
     *
     * @param budgetId    ID del presupuesto activo
     * @param reward      Monto de la recompensa al usuario
     * @param referenceId ID externo de la sesión de juego (para auditoría)
     */
    @Transactional
    public void consumeForGameReward(Long budgetId, BigDecimal reward, String referenceId) {
        consume(budgetId, reward, TransactionType.GAME_REWARD, referenceId,
                "Recompensa por sesión de juego branded");
    }

    /**
     * Ajuste manual del presupuesto (soporte, correcciones).
     *
     * @param budgetId    ID del presupuesto
     * @param amount      Monto a descontar (positivo)
     * @param description Descripción del ajuste
     */
    @Transactional
    public void applyManualAdjustment(Long budgetId, BigDecimal amount, String description) {
        consume(budgetId, amount, TransactionType.MANUAL_ADJUSTMENT, null, description);
    }

    // ── Implementación interna ────────────────────────────────────────────────

    private void consume(Long budgetId, BigDecimal amount, TransactionType type,
            String referenceId, String description) {

        Investment budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Presupuesto no encontrado: " + budgetId));

        if (!budget.getStatus().equals(InvestmentStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "No se puede consumir un presupuesto inactivo: " + budgetId);
        }

        boolean wasActive = budget.hasFunds();
        budget.consume(amount); // Lanza excepción si saldo insuficiente
        budgetRepository.save(budget);

        // Registrar transacción para trazabilidad
        BudgetTransaction tx = BudgetTransaction.builder()
                .budget(budget)
                .amount(amount)
                .type(type)
                .referenceId(referenceId)
                .description(description)
                .createdAt(ZonedDateTime.now())
                .build();
        transactionRepository.save(tx);

        log.debug("Budget {} consumido: {} por {}. Saldo restante: {}",
                budgetId, amount, type, budget.getRemainingAmount());

        // Si el budget se agotó en esta transacción, notificar a InvestmentService
        if (wasActive && !budget.hasFunds()) {
            log.info("Budget {} agotado. Notificando cambio a modo BASIC.", budgetId);
            investmentService.markBudgetExhausted(budget.getId());
        }
    }
}