package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.controllers.PlanController.InvestmentResponse;
import com.verygana2.models.plans.Investment;
import com.verygana2.models.plans.Investment.InvestmentStatus;
import com.verygana2.models.plans.Plan;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.plans.InvestmentRepository;
import com.verygana2.repositories.plans.PlanRepository;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestiona el ciclo de vida de inversiones publicitarias.
 *
 * Responsabilidades:
 *  - Registrar nuevas inversiones y crear el Budget asociado.
 *  - Validar que el monto sea suficiente para STANDARD o PREMIUM.
 *  - Actualizar recoveredAmount cuando se registran ventas.
 *  - Detectar y marcar cuando se alcanza el ROI x6.
 *  - Cerrar inversiones al agotarse el presupuesto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentService {

    private static final BigDecimal STANDARD_MIN = new BigDecimal("1000000");
    private static final BigDecimal REINVEST_THRESHOLD = new BigDecimal("500000");
    private static final BigDecimal ROI_MULTIPLIER = BigDecimal.valueOf(6);

    private final InvestmentRepository investmentRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final PlanRepository planRepository;

    /**
     * Registra una nueva inversión y activa el presupuesto correspondiente.
     *
     * @param commercial         Anunciante que realiza la inversión
     * @param investmentAmount   Monto total de la inversión
     * @return La inversión creada con su Budget asociado
     * @throws ValidationException si el monto no alcanza el mínimo de STANDARD
     */
    @Transactional
    public InvestmentResponse createInvestment(Long commercialId, BigDecimal investmentAmount) {

        CommercialDetails commercial = commercialDetailsRepository
                .findById(commercialId)
                .orElseThrow(() -> new ValidationException(
                        "Anunciante no encontrado: " + commercialId));

        Investment activeInvestment = investmentRepository
                .findActiveByCommercialId(commercialId)
                .orElse(null);

        BigDecimal finalInvestmentAmount = investmentAmount;

        // ── Manejo de inversión activa ───────────────────────────────────────────
        if (activeInvestment != null && activeInvestment.hasFunds()) {

            BigDecimal remaining = activeInvestment.getRemainingAmount();

            if (remaining.compareTo(REINVEST_THRESHOLD) > 0) {
                throw new ValidationException(
                    "Debes consumir tu saldo actual antes de reinvertir. Saldo restante: " + remaining
                );
            }

            // Sumar saldo restante a la nueva inversión
            finalInvestmentAmount = finalInvestmentAmount.add(remaining);

            // Cerrar inversión anterior
            activeInvestment.closeInvestment();
            investmentRepository.save(activeInvestment);

            log.info("Inversión previa {} cerrada. Saldo {} agregado a nueva inversión", activeInvestment.getId(), remaining);
        }

        // ── Validar mínimo ───────────────────────────────────────────────────────
        if (finalInvestmentAmount.compareTo(STANDARD_MIN) < 0) {
            throw new ValidationException(
                    "El monto mínimo para invertir es " + STANDARD_MIN
            );
        }

        // ── Determinar plan ──────────────────────────────────────────────────────
        BigDecimal finalAmount = finalInvestmentAmount;
        Plan plan = planRepository.findEligiblePlans(finalAmount)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "No se encontró un plan elegible para el monto: " + finalAmount));

        ZonedDateTime now = ZonedDateTime.now();

        // ── Crear nueva inversión ────────────────────────────────────────────────
        Investment investment = Investment.builder()
                .commercial(commercial)
                .plan(plan)
                .investmentAmount(finalInvestmentAmount)
                .remainingAmount(finalInvestmentAmount)
                .recoveredAmount(BigDecimal.ZERO)
                .roiReached(false)
                .status(InvestmentStatus.ACTIVE)
                .createdAt(now)
                .build();

        investment = investmentRepository.save(investment);

        log.info("Nueva inversión {} creada para anunciante {}. Plan: {}. Monto final: {}",
                investment.getId(),
                commercialId,
                plan.getCode(),
                finalInvestmentAmount);

        return new InvestmentResponse(finalAmount);
    }

    /**
     * Registra ventas recuperadas bajo una inversión activa y verifica el ROI.
     *
     * Este método debe llamarse cada vez que se completa una venta atribuible
     * al anunciante, independientemente de si ya alcanzó el ROI o no.
     *
     * @param investmentId  ID de la inversión activa
     * @param saleAmount    Monto de la venta completada
     * @return true si con esta venta se alcanzó el ROI x6 por primera vez
     */
    @Transactional
    public boolean registerSaleRecovery(Long investmentId, BigDecimal saleAmount) {
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ValidationException(
                        "Inversión no encontrada: " + investmentId));

        if (InvestmentStatus.CLOSED.equals(investment.getStatus())) {
            log.warn("Intento de registrar venta en inversión cerrada {}", investmentId);
            return false;
        }

        investment.setRecoveredAmount(investment.getRecoveredAmount().add(saleAmount));

        boolean roiJustReached = false;

        if (!investment.isRoiReached()) { // && investment.checkRoiThreshold()
            investment.setRoiReached(true);
            investment.setRoiReachedAt(ZonedDateTime.now());
            roiJustReached = true;

            log.info("¡ROI x6 alcanzado! Inversión {} del anunciante {}. " +
                     "Recuperado: {} / Umbral: {}",
                    investmentId, investment.getCommercial().getId(),
                    investment.getRecoveredAmount(),
                    investment.getInvestmentAmount().multiply(ROI_MULTIPLIER));
        }

        investmentRepository.save(investment);
        return roiJustReached;
    }

    /**
     * Marca la inversión como BUDGET_EXHAUSTED cuando su Budget se agota.
     * Llamado por BudgetService al detectar remainingAmount == 0.
     */
    @Transactional
    public void markBudgetExhausted(Long investmentId) {
        investmentRepository.findById(investmentId).ifPresent(inv -> {
            if (InvestmentStatus.ACTIVE.equals(inv.getStatus())) {
                inv.setStatus(InvestmentStatus.EXHAUSTED);
                investmentRepository.save(inv);
                log.info("Inversión {} marcada como BUDGET_EXHAUSTED. " +
                         "El anunciante {} opera ahora en modo BASIC.",
                        investmentId, inv.getCommercial().getId());
            }
        });
    }
}