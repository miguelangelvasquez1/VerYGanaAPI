package com.verygana2.services.plans;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.controllers.PlanController.InvestmentResponse;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.plans.InvestmentRepository;
import com.verygana2.repositories.plans.PlanRepository;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentService {

    private static final BigDecimal STANDARD_MIN_COP = new BigDecimal("1000000");
    private static final long CENTS_PER_COP = 100L;

    private final InvestmentRepository investmentRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final WalletRepository walletRepository;
    private final PlanRepository planRepository;

    /**
     * Registra un depósito publicitario.
     *
     * El saldo existente en el wallet se acumula con el nuevo depósito para
     * determinar el plan (nunca se pierde saldo por hacer un top-up).
     * El plan se recalcula sobre el balance total resultante.
     */
    @Transactional
    public InvestmentResponse createInvestment(Long commercialId, BigDecimal depositAmountCOP) {

        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new ValidationException(
                        "Comercial no encontrado: " + commercialId));

        Wallet wallet = walletRepository.findByCommercialId(commercialId)
                .orElseGet(() -> walletRepository.save(Wallet.createFor(commercial)));

        long depositCents = toCents(depositAmountCOP);
        BigDecimal newTotalCOP = toCOP(wallet.getBalanceCents() + depositCents);

        if (newTotalCOP.compareTo(STANDARD_MIN_COP) < 0) {
            throw new ValidationException(
                    "El monto mínimo para invertir es " + STANDARD_MIN_COP + " COP. " +
                    "Saldo total resultante: " + newTotalCOP);
        }

        Plan plan = planRepository.findEligiblePlans(newTotalCOP)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "No hay plan elegible para el monto: " + newTotalCOP));

        wallet.deposit(depositCents);
        walletRepository.save(wallet);

        commercial.setCurrentPlan(plan);
        commercialDetailsRepository.save(commercial);

        Investment deposit = Investment.builder()
                .wallet(wallet)
                .planAtDeposit(plan)
                .depositAmountCents(depositCents)
                .build();
        investmentRepository.save(deposit);

        log.info("Depósito #{} registrado. Comercial {}. Plan: {}. Saldo wallet: {} centavos",
                deposit.getId(), commercialId, plan.getCode(), wallet.getBalanceCents());

        return new InvestmentResponse(depositAmountCOP);
    }

    /**
     * Limpia el plan activo del comercial cuando su wallet se agota.
     * Llamado por BudgetService al detectar que wallet.isExhausted().
     */
    @Transactional
    public void handleWalletExhausted(Long commercialId) {
        commercialDetailsRepository.findById(commercialId).ifPresent(commercial -> {
            commercial.setCurrentPlan(null);
            commercialDetailsRepository.save(commercial);
            log.info("Comercial {} sin plan activo: wallet agotado.", commercialId);
        });
    }

    // ── Helpers de conversión ─────────────────────────────────────────────────

    private long toCents(BigDecimal cop) {
        return cop.multiply(BigDecimal.valueOf(CENTS_PER_COP)).longValueExact();
    }

    private BigDecimal toCOP(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(CENTS_PER_COP));
    }
}
