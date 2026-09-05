package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.finance.responses.InvestmentResponseDTO;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;

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
    private final EmailService emailService;
    private final NotificationService notificationService;

    /**
     * Registra un depósito publicitario.
     *
     * El saldo existente en el wallet se acumula con el nuevo depósito (nunca se
     * pierde saldo por hacer un top-up). El depósito nunca cambia el plan del
     * comercial — el cambio de plan es siempre una acción explícita, nunca
     * derivada del monto invertido.
     */
    @Transactional
    public InvestmentResponseDTO createInvestment(Long commercialId, BigDecimal depositAmountCOP) {

        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new ValidationException(
                        "Comercial no encontrado: " + commercialId));

        Plan plan = commercial.getCurrentPlan();
        if (plan == null) {
            throw new ValidationException(
                    "El comercial no tiene un plan activo — solicite un cambio de plan antes de invertir.");
        }

        Wallet wallet = walletRepository.findByCommercialId(commercialId)
                .orElseGet(() -> walletRepository.save(Wallet.createFor(commercial)));

        long depositCents = toCents(depositAmountCOP);
        BigDecimal newTotalCOP = toCOP(wallet.getBalanceCents() + depositCents);

        if (newTotalCOP.compareTo(STANDARD_MIN_COP) < 0) {
            throw new ValidationException(
                    "El monto mínimo para invertir es " + STANDARD_MIN_COP + " COP. " +
                    "Saldo total resultante: " + newTotalCOP);
        }

        wallet.registerDeposit(depositCents);
        walletRepository.save(wallet);

        Investment deposit = Investment.builder()
                .wallet(wallet)
                .planAtDeposit(plan)
                .depositAmountCents(depositCents)
                .build();
        investmentRepository.save(deposit);

        log.info("Depósito #{} registrado. Comercial {}. Plan: {}. Saldo wallet: {} centavos",
                deposit.getId(), commercialId, plan.getCode(), wallet.getBalanceCents());

        return new InvestmentResponseDTO(depositAmountCOP);
    }

    /**
     * Notifica al comercial cuando su wallet se agota. NO toca el plan — la
     * suspensión (bloqueo de creación de activos nuevos y de exportar reportes)
     * se deriva en tiempo real de Wallet.status == EXHAUSTED vía
     * {@link PlanFeatureGuard#assertBudgetAvailable}, así que no hay ningún
     * campo que mutar aquí. Llamado por BudgetService al detectar wallet.isExhausted().
     */
    @Transactional
    public void handleWalletExhausted(Long commercialId) {
        commercialDetailsRepository.findById(commercialId).ifPresent(commercial -> {
            log.info("Comercial {} sin presupuesto: wallet agotado. Creación de activos nuevos bloqueada.", commercialId);
            emailService.sendBudgetExhaustedEmail(commercial.getUser().getEmail(), commercial.getCompanyName());
            notificationService.createInternalNotification(commercial.getUser().getId(),
                    "Saldo publicitario agotado",
                    "Tu billetera llegó a $0 — recárgala para seguir creando anuncios, campañas y encuestas.",
                    Instant.now());
        });
    }

    /**
     * Notifica al comercial que su wallet salió de EXHAUSTED tras una recarga —
     * la suspensión se levanta sola, esto es solo el aviso.
     */
    @Transactional
    public void handleWalletReplenished(Long commercialId) {
        commercialDetailsRepository.findById(commercialId).ifPresent(commercial -> {
            log.info("Comercial {} recuperó presupuesto tras agotamiento.", commercialId);
            emailService.sendBudgetReplenishedEmail(commercial.getUser().getEmail(), commercial.getCompanyName());
            notificationService.createInternalNotification(commercial.getUser().getId(),
                    "Saldo publicitario restaurado",
                    "Tu billetera fue recargada — ya puedes volver a crear anuncios, campañas y encuestas.",
                    Instant.now());
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
