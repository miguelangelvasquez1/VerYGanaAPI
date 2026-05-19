package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.finance.WalletService;
import com.verygana2.services.wompi.WompiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

        private final WompiService wompiService;
        private final WompiTransactionRepository wompiTransactionRepository;
        private final CommercialDetailsRepository commercialDetailsRepository;
        private final TreasuryService treasuryService;
        private final TreasuryConfig treasuryConfig;
        private final SubscriptionRepository subscriptionRepository;
        private final InvestmentRepository investmentRepository;
        private final PlanRepository planRepository;
        private final WalletRepository walletRepository;
        private final WalletService walletService;

        // =========================================================================
        // PASO 1: INICIAR PAGO
        // =========================================================================

        /**
         * Punto de entrada único para iniciar el pago de cualquier tipo de plan.
         *
         * Para BASIC:
         * - Valida que no haya suscripción activa vigente
         * - Crea Subscription(PENDING_PAYMENT) con la referencia Wompi
         * - Genera checkout por el precio fijo del plan
         *
         * Para STANDARD / PREMIUM:
         * - Valida que el monto esté dentro del rango del plan
         * - Crea Investment(confirmed=false) con la referencia Wompi
         * - Genera checkout por el monto ingresado
         *
         * @param commercial  empresario que quiere pagar
         * @param planCode    BASIC, STANDARD o PREMIUM
         * @param amountCents monto a depositar. Para BASIC se ignora
         */
        @Transactional
        @Override
        public WompiCheckoutResponseDTO initiatePlanPayment(
                        CommercialDetails commercial,
                        PlanCode planCode,
                        Long amountCents) {

                log.info("[PLAN] Iniciando pago: commercialId={}, plan={}, amount={}",
                                commercial.getId(), planCode, amountCents);

                Plan plan = planRepository.findByCodeAndActiveTrue(planCode)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Plan not found or inactive: " + planCode));
                log.info("Plan encontrado: {}, code: {}, id: {}", plan.getName(), plan.getCode(), plan.getId());

                long finalAmount = resolveAmount(commercial, plan, amountCents);

                // Construir referencia única para este checkout
                String prefix = planCode == PlanCode.BASIC ? "VG-SUB" : "VG-DEP";
                String reference = prefix + "-" +
                                commercial.getUser().getPublicId().toString().replace("-", "").substring(0, 12) + "-" +
                                System.currentTimeMillis();

                // Crear registro pendiente ANTES de generar el checkout
                // Si el servidor cae entre el checkout y el webhook, el registro existe
                if (planCode == PlanCode.BASIC) {
                        createPendingSubscription(commercial, plan, finalAmount, reference);
                } else {
                        createPendingInvestment(commercial, plan, finalAmount, reference);
                }

                // Determinar tipo de transacción Wompi
                WompiTransactionType type = planCode == PlanCode.BASIC
                                ? WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION
                                : WompiTransactionType.CHARGE_BUSINESS_DEPOSIT;

                WompiCheckoutRequestDTO request = WompiCheckoutRequestDTO.builder()
                                .reference(reference)
                                .amountInCents(finalAmount)
                                .customerEmail(commercial.getUser().getEmail())
                                .redirectUrl("http://verygana.com/empresario/plan/resultado")
                                .build();

                WompiCheckoutResponseDTO response = wompiService.createCheckoutUrl(request, type);

                log.info("[PLAN] Checkout generado: reference={}, type={}, amount={}",
                                reference, type, finalAmount);

                return response;
        }

        // =========================================================================
        // PASO 2: PROCESAR WEBHOOK
        // =========================================================================

        /**
         * Punto de entrada único para el dispatcher cuando llega un webhook de plan.
         * Bifurca internamente según el tipo de transacción.
         */
        @Transactional
        @Override
        public void handleWompiResult(UUID wompiTransactionId) {

                // Recargar la entidad DENTRO de esta transacción — con todas sus relaciones
                WompiTransaction wompiTx = wompiTransactionRepository
                                .findById(Objects.requireNonNull(wompiTransactionId))
                                .orElseThrow(() -> new IllegalStateException(
                                                "WompiTransaction no encontrada: " + wompiTransactionId));

                log.info("[PLAN] Procesando webhook: type={}, reference={}, status={}",
                                wompiTx.getType(), wompiTx.getReference(), wompiTx.getStatus());

                switch (wompiTx.getStatus()) {
                        case APPROVED -> {
                                if (wompiTx.getType() == WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION) {
                                        activateSubscription(wompiTx);
                                } else {
                                        activateInvestment(wompiTx);
                                }
                        }
                        case DECLINED, ERROR -> handleFailedPayment(wompiTx);
                        case VOIDED -> log.info("[PLAN] Pago anulado: reference={}", wompiTx.getReference());
                        default -> log.warn("[PLAN] Status inesperado: {}", wompiTx.getStatus());
                }
        }

        @Override
        @Transactional(readOnly = true)
        public PlanPaymentStatusResponseDTO getPaymentStatus(String reference, CommercialDetails commercial) {

                // Buscar primero en Subscription (plan básico)
                Optional<Subscription> subscription = subscriptionRepository
                                .findByWompiReference(reference);

                if (subscription.isPresent()) {
                        Subscription sub = subscription.get();

                        // Verificar que la suscripción pertenece al commercial autenticado
                        if (!sub.getCommercial().getId().equals(commercial.getId())) {
                                throw new IllegalArgumentException("Referencia no encontrada");
                        }

                        String message = switch (sub.getStatus()) {
                                case ACTIVE -> "Tu suscripción fue activada exitosamente.";
                                case PENDING_PAYMENT -> "Tu pago está siendo procesado...";
                                case PAYMENT_FAILED -> "El pago fue rechazado. Intenta de nuevo.";
                                case EXPIRED -> "Esta suscripción ha vencido.";
                                case RENEWED -> "Suscripción renovada exitosamente.";
                                case CANCELLED -> "Suscripción cancelada.";
                        };

                        return PlanPaymentStatusResponseDTO.builder()
                                        .reference(reference)
                                        .wompiStatus(sub.getWompiTransaction() != null
                                                        ? sub.getWompiTransaction().getStatus().name()
                                                        : "PENDING")
                                        .planStatus(sub.getStatus().name())
                                        .planCode(sub.getPlan().getCode())
                                        .message(message)
                                        .build();
                }

                // Buscar en Investment (planes estándar/premium)
                Optional<Investment> investment = investmentRepository
                                .findByWompiReference(reference);

                if (investment.isPresent()) {
                        Investment inv = investment.get();

                        if (!inv.getWallet().getCommercial().getId().equals(commercial.getId())) {
                                throw new IllegalArgumentException("Referencia no encontrada");
                        }

                        String message = inv.getConfirmed()
                                        ? "Tu depósito fue acreditado. Plan " +
                                                        inv.getPlanAtDeposit().getName() + " activo."
                                        : "Tu pago está siendo procesado...";

                        return PlanPaymentStatusResponseDTO.builder()
                                        .reference(reference)
                                        .wompiStatus(inv.getWompiTransaction() != null
                                                        ? inv.getWompiTransaction().getStatus().name()
                                                        : "PENDING")
                                        .planStatus(inv.getConfirmed() ? "ACTIVE" : "PENDING_PAYMENT")
                                        .planCode(inv.getPlanAtDeposit().getCode())
                                        .message(message)
                                        .build();
                }

                throw new IllegalArgumentException(
                                "No se encontró ningún pago con reference: " + reference);
        }

        // =========================================================================
        // PLAN BÁSICO
        // =========================================================================

        private void createPendingSubscription(
                        CommercialDetails commercial, Plan plan,
                        long amountCents, String reference) {

                // Marcar la activa anterior como RENEWED si existe
                subscriptionRepository
                                .findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE)
                                .ifPresent(existing -> {
                                        existing.markAsRenewed();
                                        subscriptionRepository.save(existing);
                                });

                Subscription pending = Subscription.builder()
                                .commercial(commercial)
                                .plan(plan)
                                .wompiReference(reference)
                                .amountPaidCents(amountCents)
                                .status(SubscriptionStatus.PENDING_PAYMENT)
                                .build();

                subscriptionRepository.save(Objects.requireNonNull(pending));
                log.info("[PLAN] Subscription PENDING_PAYMENT creada: reference={}", reference);
        }

        private void activateSubscription(WompiTransaction wompiTx) {
                // Lookup por referencia — sin necesitar commercial en WompiTransaction
                Subscription subscription = subscriptionRepository
                                .findByWompiReference(wompiTx.getReference())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Subscription no encontrada para reference: " +
                                                                wompiTx.getReference()));

                subscription.activate(wompiTx);
                subscriptionRepository.save(subscription);

                CommercialDetails commercial = subscription.getCommercial();
                Plan basicPlan = planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)
                                .orElseThrow(() -> new IllegalStateException("Plan BASIC no encontrado"));
                commercial.setCurrentPlan(basicPlan);
                commercialDetailsRepository.save(commercial);

                log.info("[PLAN] Suscripción activada: commercialId={}, endDate={}",
                                subscription.getCommercial().getId(), subscription.getEndDate());

                // Registrar en tesorería — todo a OPERATIONS
                treasuryService.distributeSubscription(
                                wompiTx.getAmountInCents(),
                                subscription.getCommercial(),
                                wompiTx.getId());
        }

        // =========================================================================
        // PLANES ESTÁNDAR Y PREMIUM
        // =========================================================================

        private void createPendingInvestment(
                        CommercialDetails commercial, Plan plan,
                        long amountCents, String reference) {

                // Obtener el Wallet del empresario
                Wallet wallet = walletRepository.findByCommercialId(commercial.getId())
                                .orElseGet(() -> walletService.createFor(commercial.getId()));

                Investment pending = Investment.builder()
                                .wallet(wallet)
                                .planAtDeposit(plan)
                                .wompiReference(reference)
                                .depositAmountCents(amountCents)
                                .confirmed(false)
                                .build();

                investmentRepository.save(Objects.requireNonNull(pending));
                log.info("[PLAN] Investment pendiente creado: reference={}, amount={}",
                                reference, amountCents);
        }

        // ─── Reemplazar el método activateInvestment en PlanServiceImpl ──────────────
        // Los demás métodos permanecen igual.

        private void activateInvestment(WompiTransaction wompiTx) {
                // 1. Lookup por referencia Wompi
                Investment investment = investmentRepository
                                .findByWompiReference(wompiTx.getReference())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Investment no encontrado para reference: " +
                                                                wompiTx.getReference()));

                // 2. Confirmar el depósito (marca confirmed=true, guarda wompiTx)
                investment.confirm(wompiTx);
                investmentRepository.save(investment);

                Wallet wallet = investment.getWallet();
                boolean wasExhausted = wallet.isExhausted();
                
                // 3. Calculo de deposito a la wallet (descontando comision para operaciones verygana y fondo de fortalecimiento)
                BigDecimal amount = BigDecimal.valueOf(wompiTx.getAmountInCents()) // long exacto
                                .multiply(BigDecimal.valueOf(treasuryConfig.getKeysReservePct())) // int exacto
                                .divide(BigDecimal.valueOf(100));

                // 4. Acreditar saldo — deposit() recalcula el status automáticamente
                wallet.deposit(amount.longValue());

                walletRepository.save(wallet);

                CommercialDetails commercial = wallet.getCommercial();

                // 5. Calcular total histórico invertido (todos los depósitos confirmados)
                long totalInvestedCents = investmentRepository
                                .findByWalletAndConfirmedTrue(wallet)
                                .stream()
                                .mapToLong(Investment::getDepositAmountCents)
                                .sum();

                // 6. Determinar el plan correcto según total histórico
                // El plan NUNCA baja — solo sube cuando el total acumulado supera el umbral
                Plan correctPlan = resolvePlanByTotalInvested(totalInvestedCents);
                commercial.setCurrentPlan(correctPlan);

                log.info("[PLAN] Inversión activada: commercialId={}, amount={}, " +
                                "totalInvested={}, plan={}, walletStatus={}",
                                commercial.getId(), wompiTx.getAmountInCents(),
                                totalInvestedCents, correctPlan.getCode(), wallet.getStatus());

                // 7. Si el wallet estaba EXHAUSTED, reactivar todas las interacciones pausadas
                // El dispatcher de interacciones escucha este evento y reactiva todo
                if (wasExhausted) {
                        log.info("[PLAN] Wallet reactivado después de agotamiento: " +
                                        "commercialId={} — reactivando interacciones", commercial.getId());
                        // TODO (fase siguiente): interactionService.reactivateAll(commercial);
                        // Este método buscará todas las interacciones del comercial con
                        // status=PAUSED_BY_BALANCE y las volverá a ACTIVE
                }

                // 8. Distribuir en tesorería — 60% KEYS_RESERVE / 10% FORTIFICATION / 30%
                // OPERATIONS
                treasuryService.distributeDeposit(
                                wompiTx.getAmountInCents(),
                                commercial,
                                wompiTx.getId());
        }

        /**
         * Determina el plan según el total histórico invertido.
         * El plan NUNCA se degrada — siempre retorna el mejor plan al que califique.
         *
         * Ejemplo:
         * totalInvested = $3M → STANDARD
         * totalInvested = $11M → PREMIUM
         * totalInvested = $0 → error (no debería llegar aquí)
         */
        private Plan resolvePlanByTotalInvested(long totalInvestedCents) {
                // Verificar PREMIUM primero (el de mayor umbral)
                Optional<Plan> premium = planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM);
                if (premium.isPresent()
                                && premium.get().getMinInvestmentCents() != null
                                && totalInvestedCents >= premium.get().getMinInvestmentCents()) {
                        return premium.get();
                }

                // Luego STANDARD
                Optional<Plan> standard = planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD);
                if (standard.isPresent()
                                && standard.get().getMinInvestmentCents() != null
                                && totalInvestedCents >= standard.get().getMinInvestmentCents()) {
                        return standard.get();
                }

                // No debería llegar aquí porque validateInvestmentAmount
                // ya garantizó que el monto mínimo fue respetado en el primer depósito
                throw new IllegalStateException(
                                "No se encontró plan para totalInvested=" + totalInvestedCents);
        }

        // =========================================================================
        // JOBS PROGRAMADOS
        // =========================================================================

        /**
         * Expira suscripciones vencidas. Corre a las 00:05 AM Colombia (05:05 UTC).
         */
        @Scheduled(cron = "${subscription.expiry-cron:0 5 5 * * *}")
        @Transactional
        public void expireSubscriptions() {
                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                List<Subscription> expired = subscriptionRepository.findExpiredActive(now);

                if (expired.isEmpty()) {
                        log.info("[PLAN JOB] No hay suscripciones vencidas.");
                        return;
                }

                log.info("[PLAN JOB] Expirando {} suscripciones.", expired.size());
                expired.forEach(sub -> {
                        sub.expire();
                        subscriptionRepository.save(sub);
                        log.info("[PLAN JOB] Expirada: commercialId={}", sub.getCommercial().getId());
                        // TODO: notificationService.sendSubscriptionExpiredEmail(sub.getCommercial());
                });
        }

        /**
         * Recordatorios de renovación para suscripciones que vencen en 3 días.
         * Corre a las 2 PM Colombia (7 PM UTC).
         */
        @Scheduled(cron = "${subscription.reminder-cron:0 0 19 * * *}")
        @Transactional(readOnly = true)
        public void sendRenewalReminders() {
                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                subscriptionRepository
                                .findExpiringBetween(now, now.plusDays(3))
                                .forEach(sub -> {
                                        log.info("[PLAN JOB] Recordatorio pendiente: commercialId={}, dias={}",
                                                        sub.getCommercial().getId(), sub.daysRemaining());
                                        // TODO: notificationService.sendRenewalReminderEmail(...)
                                });
        }

        /**
         * Limpia checkouts abandonados (PENDING_PAYMENT > 2 horas sin confirmación).
         * Corre cada hora.
         */
        @Scheduled(cron = "0 0 * * * *")
        @Transactional
        public void cleanAbandonedCheckouts() {
                ZonedDateTime twoHoursAgo = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
                List<Subscription> abandoned = subscriptionRepository
                                .findAbandonedCheckouts(twoHoursAgo);

                abandoned.forEach(sub -> {
                        sub.setStatus(SubscriptionStatus.PAYMENT_FAILED);
                        subscriptionRepository.save(sub);
                        log.info("[PLAN JOB] Checkout abandonado limpiado: reference={}",
                                        sub.getWompiReference());
                });
        }

        // =========================================================================
        // PRIVADOS — utilidades
        // =========================================================================

        private long resolveAmount(CommercialDetails commercial, Plan plan, Long amountCents) {
                return switch (plan.getCode()) {
                        case BASIC -> {
                                boolean hasActive = subscriptionRepository
                                                .findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE)
                                                .map(Subscription::isCurrentlyActive)
                                                .orElse(false);
                                if (hasActive) {
                                        throw new IllegalStateException(
                                                        "El empresario ya tiene una suscripción activa vigente.");
                                }
                                if (plan.getMonthlyPriceCents() == null || plan.getMonthlyPriceCents() <= 0) {
                                        throw new IllegalStateException(
                                                        "El plan básico no tiene precio mensual configurado.");
                                }
                                yield plan.getMonthlyPriceCents();
                        }
                        case STANDARD, PREMIUM -> {
                                validateInvestmentAmount(amountCents, plan);
                                yield amountCents;
                        }
                };
        }

        private void validateInvestmentAmount(Long amountCents, Plan plan) {
                if (amountCents == null || amountCents <= 0) {
                        throw new IllegalArgumentException("El monto debe ser positivo.");
                }
                if (plan.getMinInvestmentCents() != null
                                && amountCents < plan.getMinInvestmentCents()) {
                        throw new IllegalArgumentException(
                                        "Monto mínimo para " + plan.getCode() + ": " +
                                                        plan.getMinInvestmentCents() + " centavos.");
                }
                if (plan.getMaxInvestmentCents() != null
                                && amountCents > plan.getMaxInvestmentCents()) {
                        throw new IllegalArgumentException(
                                        "Monto máximo para " + plan.getCode() + ": " +
                                                        plan.getMaxInvestmentCents() + " centavos.");
                }
        }

        private void handleFailedPayment(WompiTransaction wompiTx) {
                log.warn("[PLAN] Pago fallido: reference={}, status={}",
                                wompiTx.getReference(), wompiTx.getStatus());

                // Intentar marcar la Subscription o Investment como PAYMENT_FAILED
                subscriptionRepository.findByWompiReference(wompiTx.getReference())
                                .ifPresent(sub -> {
                                        sub.setStatus(SubscriptionStatus.PAYMENT_FAILED);
                                        subscriptionRepository.save(sub);
                                });

                // TODO: notificationService.sendPaymentFailedEmail(...)
        }

        @Override
        @Transactional(readOnly = true)
        public EffectivePlanStateResponseDTO getEffectivePlanState(CommercialDetails commercial) {
                Plan currentPlan = commercial.getCurrentPlan();

                // Sin plan activo
                if (currentPlan == null) {
                        return EffectivePlanStateResponseDTO.builder()
                                        .effectivePlan(null)
                                        .hasActivePlan(false)
                                        .remainingBudgetCents(0L)
                                        .commissionRate(0)
                                        .canAdvertise(false)
                                        .canUseGames(false)
                                        .canUseSurveys(false)
                                        .maxProducts(0)
                                        .maxAds(0)
                                        .maxBrandedGames(0)
                                        .maxSurveys(0)
                                        .maxKeysPct(0)
                                        .walletStatus("INACTIVE")
                                        .build();
                }

                // ── Plan BASIC ────────────────────────────────────────────────────────────
                if (currentPlan.getCode() == PlanCode.BASIC) {

                        Optional<Subscription> activeSub = subscriptionRepository
                                        .findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE);

                        boolean hasActive = activeSub.map(Subscription::isCurrentlyActive).orElse(false);
                        Long daysRemaining = activeSub.map(Subscription::daysRemaining).orElse(0L);

                        return EffectivePlanStateResponseDTO.builder()
                                        .effectivePlan(PlanCode.BASIC.name())
                                        .hasActivePlan(hasActive)
                                        .remainingBudgetCents(0L)
                                        .commissionRate(currentPlan.getSaleCommissionPct())
                                        .canAdvertise(currentPlan.getBoolFeature("CAN_ADVERTISE", false))
                                        .canUseGames(currentPlan.getBoolFeature("CAN_USE_GAMES", false))
                                        .canUseSurveys(currentPlan.getBoolFeature("CAN_USE_SURVEYS", false))
                                        .maxProducts(currentPlan.getIntFeature("MAX_PRODUCTS", 10))
                                        .maxAds(currentPlan.getIntFeature("MAX_ADS", 0))
                                        .maxBrandedGames(currentPlan.getIntFeature("MAX_BRANDED_GAMES", 0))
                                        .maxSurveys(currentPlan.getIntFeature("MAX_SURVEYS", 0))
                                        .maxKeysPct(currentPlan.getMaxKeysPct())
                                        .subscriptionDaysRemaining(hasActive ? daysRemaining : 0L)
                                        .walletStatus("INACTIVE") // BASIC no tiene wallet de presupuesto
                                        .build();
                }

                // ── Plan STANDARD / PREMIUM ───────────────────────────────────────────────
                Wallet wallet = commercial.getWallet();
                boolean hasActivePlan = wallet != null && wallet.isOperational();
                String walletStatus = wallet != null ? wallet.getStatus().name() : "INACTIVE";
                long remainingBudgetCents = wallet != null ? wallet.getBalanceCents() : 0L;

                return EffectivePlanStateResponseDTO.builder()
                                .effectivePlan(currentPlan.getCode().name())
                                .hasActivePlan(hasActivePlan)
                                .remainingBudgetCents(remainingBudgetCents)
                                .commissionRate(currentPlan.getSaleCommissionPct())
                                .canAdvertise(currentPlan.getBoolFeature("CAN_ADVERTISE", false))
                                .canUseGames(currentPlan.getBoolFeature("CAN_USE_GAMES", false))
                                .canUseSurveys(currentPlan.getBoolFeature("CAN_USE_SURVEYS", false))
                                .maxProducts(currentPlan.getIntFeature("MAX_PRODUCTS", 100))
                                .maxAds(currentPlan.getIntFeature("MAX_ADS", 0))
                                .maxBrandedGames(currentPlan.getIntFeature("MAX_BRANDED_GAMES", 0))
                                .maxSurveys(currentPlan.getIntFeature("MAX_SURVEYS", 0))
                                .maxKeysPct(currentPlan.getMaxKeysPct())
                                .subscriptionDaysRemaining(null)
                                .walletStatus(walletStatus)
                                .build();
        }

}
