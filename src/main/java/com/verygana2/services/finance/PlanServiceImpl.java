package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO;
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO.NextAction;
import com.verygana2.dtos.finance.plans.responses.PlanCatalogResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.finance.plans.responses.RechargePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanOptionDTO;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidAmountException;
import com.verygana2.exceptions.RechargeAlreadyPaidException;
import com.verygana2.mappers.CommercialOnboardingMapper;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.services.interfaces.finance.ProsperityService;
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
        private final ProsperityService prosperityService;
        private final TreasuryConfig treasuryConfig;
        private final SubscriptionRepository subscriptionRepository;
        private final InvestmentRepository investmentRepository;
        private final PlanRepository planRepository;
        private final WalletRepository walletRepository;
        private final WalletService walletService;
        private final CommercialOnboardingRepository onboardingRepository;
        private final com.verygana2.services.plans.InvestmentService investmentService;
        private final com.verygana2.services.plans.EffectivePlanResolver effectivePlanResolver;
        private final com.verygana2.services.interfaces.EmailService emailService;
        private final CommercialContractService commercialContractService;
        private final CommercialContractRepository commercialContractRepository;
        private final PlanChangeRequestRepository planChangeRequestRepository;
        private final com.verygana2.services.interfaces.finance.PlanChangeRequestService planChangeRequestService;
        private final CommercialOnboardingMapper commercialOnboardingMapper;

        // Una recarga (otrosí generado) que no se paga en este lapso se cancela sola —
        // ver RechargeExpiryScheduler. Sin esto queda "en curso" para siempre y bloquea
        // pedir otra recarga o un cambio de plan.
        @Value("${commercial.contract.recharge-expiry.max-age-hours:24}")
        private long rechargeMaxAgeHours;

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

                requireLegacyPaymentAllowed(commercial, planCode);

                long baseAmount = resolveAmount(commercial, plan, amountCents);
                long vatAmount = vatFor(baseAmount);
                long totalAmount = baseAmount + vatAmount;

                // Construir referencia única para este checkout
                String prefix = planCode == PlanCode.BASIC ? "VG-SUB" : "VG-DEP";
                String reference = prefix + "-" +
                                commercial.getUser().getPublicId().toString().replace("-", "").substring(0, 12) + "-" +
                                System.currentTimeMillis();

                // Crear registro pendiente ANTES de generar el checkout
                // Si el servidor cae entre el checkout y el webhook, el registro existe
                if (planCode == PlanCode.BASIC) {
                        createPendingSubscription(commercial, plan, baseAmount, vatAmount, reference);
                } else {
                        createPendingInvestment(commercial, plan, baseAmount, vatAmount, reference);
                }

                // Determinar tipo de transacción Wompi
                WompiTransactionType type = planCode == PlanCode.BASIC
                                ? WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION
                                : WompiTransactionType.CHARGE_BUSINESS_DEPOSIT;

                // Wompi cobra base + IVA; el desglose queda snapshoteado en el
                // Investment/Subscription pendiente para que activateInvestment/
                // activateSubscription distribuyan cada parte a su cuenta de tesorería.
                WompiCheckoutRequestDTO request = WompiCheckoutRequestDTO.builder()
                                .reference(reference)
                                .amountInCents(totalAmount)
                                .customerEmail(commercial.getUser().getEmail())
                                .redirectUrl("http://verygana.com/empresario/plan/resultado")
                                .build();

                WompiCheckoutResponseDTO response = wompiService.createCheckoutUrl(request, type);

                log.info("[PLAN] Checkout generado: reference={}, type={}, base={}, vat={}, total={}",
                                reference, type, baseAmount, vatAmount, totalAmount);

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

                        String planStatus;
                        String message;
                        if (inv.getConfirmed()) {
                                planStatus = "ACTIVE";
                                message = "Tu depósito fue acreditado. Plan " +
                                                inv.getPlanAtDeposit().getName() + " activo.";
                        } else if (inv.getFailedAt() != null) {
                                planStatus = "PAYMENT_FAILED";
                                message = "El pago fue rechazado. Intenta de nuevo.";
                        } else {
                                planStatus = "PENDING_PAYMENT";
                                message = "Tu pago está siendo procesado...";
                        }

                        return PlanPaymentStatusResponseDTO.builder()
                                        .reference(reference)
                                        .wompiStatus(inv.getWompiTransaction() != null
                                                        ? inv.getWompiTransaction().getStatus().name()
                                                        : "PENDING")
                                        .planStatus(planStatus)
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

        private Subscription createPendingSubscription(
                        CommercialDetails commercial, Plan plan,
                        long amountCents, long vatAmountCents, String reference) {

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
                                .vatAmountCents(vatAmountCents)
                                .status(SubscriptionStatus.PENDING_PAYMENT)
                                .build();

                Subscription saved = subscriptionRepository.save(Objects.requireNonNull(pending));
                log.info("[PLAN] Subscription PENDING_PAYMENT creada: reference={}, amount={}, vat={}",
                                reference, amountCents, vatAmountCents);
                return saved;
        }

        private void activateSubscription(WompiTransaction wompiTx) {
                // Lookup con lock pesimista — bloquea la fila hasta que esta transacción
                // haga commit, para que una segunda entrega concurrente del mismo webhook
                // espere aquí y no lea status=PENDING_PAYMENT dos veces.
                Subscription subscription = subscriptionRepository
                                .findByWompiReferenceForUpdate(wompiTx.getReference())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Subscription no encontrada para reference: " +
                                                                wompiTx.getReference()));

                // Idempotencia: si ya fue activada (por esta misma llamada en una entrega
                // anterior, o por una transacción concurrente que ya liberó el lock), no
                // repetir activate()/distributeSubscription().
                if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                        log.info("[PLAN] Subscription ya activa — entrega duplicada del webhook ignorada: reference={}",
                                        wompiTx.getReference());
                        return;
                }

                subscription.activate(wompiTx);
                subscriptionRepository.save(subscription);

                CommercialDetails commercial = subscription.getCommercial();
                Plan basicPlan = planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)
                                .orElseThrow(() -> new IllegalStateException("Plan BASIC no encontrado"));
                commercial.setCurrentPlan(basicPlan);
                commercialDetailsRepository.save(commercial);

                log.info("[PLAN] Suscripción activada: commercialId={}, endDate={}",
                                subscription.getCommercial().getId(), subscription.getEndDate());

                // Registrar en tesorería — base a OPERATIONS, IVA (si lo hubo) a TAX_RESERVE
                treasuryService.distributeSubscription(
                                subscription.getAmountPaidCents(),
                                subscription.getVatAmountCents(),
                                subscription.getCommercial(),
                                wompiTx.getId());

                completeOnboardingIfPending(commercial, basicPlan);
                applyPlanChangeIfLinked(null, subscription.getId());
        }

        // =========================================================================
        // PLANES ESTÁNDAR Y PREMIUM
        // =========================================================================

        private Investment createPendingInvestment(
                        CommercialDetails commercial, Plan plan,
                        long amountCents, long vatAmountCents, String reference) {

                // Obtener el Wallet del empresario
                Wallet wallet = walletRepository.findByCommercialId(commercial.getId())
                                .orElseGet(() -> walletService.createFor(commercial.getId()));

                Investment pending = Investment.builder()
                                .wallet(wallet)
                                .planAtDeposit(plan)
                                .wompiReference(reference)
                                .depositAmountCents(amountCents)
                                .vatAmountCents(vatAmountCents)
                                .confirmed(false)
                                .build();

                Investment saved = investmentRepository.save(Objects.requireNonNull(pending));
                log.info("[PLAN] Investment pendiente creado: reference={}, amount={}, vat={}",
                                reference, amountCents, vatAmountCents);
                return saved;
        }

        // =========================================================================
        // RECARGA STANDARD/PREMIUM CON CONTRATO — contrato primero, pago después
        // =========================================================================

        @Override
        @Transactional(readOnly = true)
        public RechargePreviewResponseDTO previewRecharge(CommercialDetails commercial, Long amountCents) {
                Plan plan = commercial.getCurrentPlan();
                long currentBalance = commercial.getWallet() != null && commercial.getWallet().getBalanceCents() != null
                                ? commercial.getWallet().getBalanceCents() : 0L;

                boolean eligible;
                String message;
                OpenRechargeResponseDTO openRecharge = null;

                if (plan == null || plan.getCode() == PlanCode.BASIC) {
                        eligible = false;
                        message = "Solo los planes STANDARD y PREMIUM pueden recargar presupuesto.";
                } else {
                        try {
                                validateInvestmentAmount(amountCents, plan);
                                openRecharge = findOpenRecharge(commercial.getId()).orElse(null);
                                if (openRecharge != null) {
                                        eligible = false;
                                        message = openRecharge.getMessage();
                                } else {
                                        List<PlanChangeRequestStatus> terminal = List.of(PlanChangeRequestStatus.APPLIED,
                                                        PlanChangeRequestStatus.REJECTED, PlanChangeRequestStatus.CANCELLED);
                                        if (!planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercial.getId(), terminal).isEmpty()) {
                                                eligible = false;
                                                message = "Tiene una solicitud de cambio de plan en curso — resuélvala antes de recargar.";
                                        } else {
                                                eligible = true;
                                                message = "Podrá continuar: se generará el otrosí de recarga y se enviará a firma electrónica de inmediato.";
                                        }
                                }
                        } catch (IllegalArgumentException ex) {
                                eligible = false;
                                message = ex.getMessage();
                        }
                }

                boolean planIsFunded = plan != null && plan.getCode() != PlanCode.BASIC;
                long estimatedCreditedAmountCents = planIsFunded && amountCents != null && amountCents > 0
                                ? BigDecimal.valueOf(amountCents).multiply(BigDecimal.valueOf(treasuryConfig.getKeysReservePct()))
                                                .divide(BigDecimal.valueOf(100)).longValue()
                                : 0L;

                // IVA sobre el monto base — es lo que realmente se cobra en Wompi al generar
                // el checkout (ver generateRechargeCheckout), así el preview no muestra un
                // número menor al que se le va a cobrar.
                Long vatAmountCents = amountCents != null ? vatFor(amountCents) : null;
                Long totalToPayCents = amountCents != null && vatAmountCents != null ? amountCents + vatAmountCents : null;

                return new RechargePreviewResponseDTO(
                                plan != null ? plan.getCode() : null,
                                eligible,
                                message,
                                centsToPesos(amountCents),
                                centsToPesos(vatAmountCents),
                                centsToPesos(totalToPayCents),
                                plan != null ? centsToPesos(plan.getMinInvestmentCents()) : null,
                                plan != null ? centsToPesos(plan.getMaxInvestmentCents()) : null,
                                centsToPesos(currentBalance),
                                centsToPesos(estimatedCreditedAmountCents),
                                centsToPesos(currentBalance + estimatedCreditedAmountCents),
                                openRecharge);
        }

        /** Los previews de recarga se muestran al comercial en pesos, no en centavos. */
        private static Long centsToPesos(Long cents) {
                return cents != null ? cents / 100 : null;
        }

        @Override
        @Transactional
        public ContractSummaryResponseDTO requestRecharge(CommercialDetails commercialArg, Long amountCents) {
                // Lock pesimista sobre la fila del comercial: serializa esta solicitud con
                // requestPlanChange (que toma el mismo lock) para que dos requests concurrentes
                // no puedan crear una recarga y un cambio de plan a la vez — los chequeos de
                // abajo son read-then-write y sin esto tienen una ventana de carrera.
                CommercialDetails commercial = commercialDetailsRepository.findByIdForUpdate(commercialArg.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Comercial no encontrado: " + commercialArg.getId()));
                Plan plan = commercial.getCurrentPlan();
                if (plan == null || plan.getCode() == PlanCode.BASIC) {
                        throw new IllegalStateException("Solo los planes STANDARD/PREMIUM pueden recargar presupuesto.");
                }
                validateInvestmentAmount(amountCents, plan);

                findOpenRecharge(commercial.getId()).ifPresent(open -> {
                        throw new BusinessException(open.getMessage());
                });
                List<PlanChangeRequestStatus> terminal = List.of(
                                PlanChangeRequestStatus.APPLIED, PlanChangeRequestStatus.REJECTED, PlanChangeRequestStatus.CANCELLED);
                if (!planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercial.getId(), terminal).isEmpty()) {
                        throw new BusinessException(
                                        "Tiene una solicitud de cambio de plan en curso — resuélvala antes de recargar.");
                }

                log.info("[PLAN] Solicitando recarga: commercialId={}, amount={}", commercial.getId(), amountCents);
                return commercialContractService.generateFor(commercial, ContractPurpose.RECHARGE, amountCents, null);
        }

        @Override
        // noRollbackFor: si la conciliación descubre que la recarga ya estaba pagada, la
        // acreditación hecha en esta misma transacción tiene que quedar aunque el
        // checkout se rechace.
        @Transactional(noRollbackFor = RechargeAlreadyPaidException.class)
        public WompiCheckoutResponseDTO generateRechargeCheckout(Long contractId, CommercialDetails commercial) {
                CommercialContract contract = commercialContractRepository.findById(contractId)
                                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + contractId));

                if (!contract.getCommercial().getId().equals(commercial.getId())) {
                        throw new IllegalArgumentException("Contrato no encontrado: " + contractId);
                }
                if (contract.getPurpose() != ContractPurpose.RECHARGE) {
                        throw new IllegalStateException("Este contrato no es de recarga.");
                }
                if (contract.getStatus() != ContractStatus.SIGNED) {
                        throw new IllegalStateException("El contrato debe estar firmado antes de generar el pago.");
                }

                // Reintento: la recarga ya tenía un checkout abierto. Antes de dejar pagar
                // otra vez se concilia con Wompi, para no cobrar dos veces un pago cuyo
                // webhook nunca llegó.
                Investment previous = contract.getInvestment();
                if (previous != null) {
                        switch (reconcilePendingPayment(previous)) {
                                case PAID -> throw new RechargeAlreadyPaidException(
                                                "Esta recarga ya fue pagada — el saldo ya está acreditado en su cuenta.");
                                case IN_PROCESS -> throw new BusinessException(
                                                "Su pago anterior todavía se está procesando en la pasarela. "
                                                                + "Espere a que se resuelva antes de intentar de nuevo.");
                                case NOT_STARTED -> {
                                        // Abrió el checkout y lo abandonó sin pagar: Wompi no tiene transacción
                                        // para esa referencia, así que se reutiliza el mismo Investment. El
                                        // monto sale de su snapshot, no de un vatPct que pudo haber cambiado.
                                        long total = previous.getDepositAmountCents() + previous.getVatAmountCents();
                                        WompiCheckoutResponseDTO resumed = wompiService.resumeCheckoutUrl(
                                                        rechargeCheckoutRequest(commercial, previous.getWompiReference(), total),
                                                        WompiTransactionType.CHARGE_BUSINESS_DEPOSIT);
                                        log.info("[PLAN] Checkout de recarga retomado: contractId={}, reference={}, total={}",
                                                        contractId, previous.getWompiReference(), total);
                                        return resumed;
                                }
                                // Pago rechazado/anulado: esa referencia ya tiene una transacción terminal
                                // en Wompi, así que el reintento sale con un Investment y referencia nuevos.
                                case FAILED -> log.info("[PLAN] Reintentando pago de recarga tras un rechazo: "
                                                + "contractId={}, referenciaAnterior={}", contractId, previous.getWompiReference());
                        }
                }

                Plan plan = commercial.getCurrentPlan();
                long amountCents = contract.getAmountCentsSnapshot();
                long vatAmountCents = vatFor(amountCents);
                long totalAmountCents = amountCents + vatAmountCents;
                String reference = "VG-DEP-" +
                                commercial.getUser().getPublicId().toString().replace("-", "").substring(0, 12) + "-" +
                                System.currentTimeMillis();

                Investment pending = createPendingInvestment(commercial, plan, amountCents, vatAmountCents, reference);
                contract.setInvestment(pending);
                commercialContractRepository.save(contract);

                WompiCheckoutResponseDTO response = wompiService.createCheckoutUrl(
                                rechargeCheckoutRequest(commercial, reference, totalAmountCents),
                                WompiTransactionType.CHARGE_BUSINESS_DEPOSIT);

                log.info("[PLAN] Checkout de recarga generado: contractId={}, reference={}, base={}, vat={}, total={}",
                                contractId, reference, amountCents, vatAmountCents, totalAmountCents);

                return response;
        }

        private WompiCheckoutRequestDTO rechargeCheckoutRequest(
                        CommercialDetails commercial, String reference, long totalAmountCents) {
                return WompiCheckoutRequestDTO.builder()
                                .reference(reference)
                                .amountInCents(totalAmountCents)
                                .customerEmail(commercial.getUser().getEmail())
                                .redirectUrl("http://verygana.com/empresario/plan/resultado")
                                .build();
        }

        // =========================================================================
        // RECARGA INTERRUMPIDA — retomar, conciliar, cancelar, vencer
        // =========================================================================

        @Override
        @Transactional(readOnly = true)
        public Optional<OpenRechargeResponseDTO> getOpenRecharge(CommercialDetails commercial) {
                return findOpenRecharge(commercial.getId());
        }

        @Override
        @Transactional
        public Optional<OpenRechargeResponseDTO> reconcileRecharge(Long contractId, CommercialDetails commercial) {
                CommercialContract contract = commercialContractRepository.findById(contractId)
                                .filter(c -> c.getCommercial().getId().equals(commercial.getId()))
                                .filter(c -> c.getPurpose() == ContractPurpose.RECHARGE)
                                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + contractId));

                if (!isOpenRecharge(contract)) {
                        return Optional.empty();
                }
                Investment investment = contract.getInvestment();
                if (investment == null) {
                        return Optional.of(toOpenRecharge(contract));
                }
                return switch (reconcilePendingPayment(investment)) {
                        case PAID -> Optional.empty();
                        case IN_PROCESS -> {
                                OpenRechargeResponseDTO dto = toOpenRecharge(contract);
                                dto.setNextAction(NextAction.WAIT_PAYMENT);
                                dto.setMessage("Su pago se está procesando en la pasarela — el saldo se acreditará "
                                                + "en cuanto se confirme.");
                                yield Optional.of(dto);
                        }
                        case NOT_STARTED, FAILED -> Optional.of(toOpenRecharge(contract));
                };
        }

        @Override
        @Transactional(noRollbackFor = RechargeAlreadyPaidException.class)
        public ContractSummaryResponseDTO cancelRecharge(Long contractId, CommercialDetails commercial) {
                // Solo se concilia si el contrato es una recarga propia con checkout abierto;
                // cualquier otro caso (no existe, es de otro, no es recarga) lo rechaza
                // cancelForCommercial con sus propios errores.
                commercialContractRepository.findById(contractId)
                                .filter(c -> c.getCommercial().getId().equals(commercial.getId()))
                                .filter(c -> c.getPurpose() == ContractPurpose.RECHARGE)
                                .map(CommercialContract::getInvestment)
                                .ifPresent(this::requireNoLivePayment);

                return commercialContractService.cancelForCommercial(contractId, commercial.getId());
        }

        @Override
        @Transactional(readOnly = true)
        public List<Long> findStaleRechargeContractIds() {
                return commercialContractRepository.findOpenRechargeContractIdsGeneratedBefore(
                                ZonedDateTime.now().minusHours(rechargeMaxAgeHours));
        }

        @Override
        @Transactional
        public boolean expireRecharge(Long contractId) {
                CommercialContract contract = commercialContractRepository.findById(contractId)
                                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + contractId));

                if (!isOpenRecharge(contract)) {
                        return false;
                }
                // Igual que al vencer copagos: antes de dar por abandonada una recarga con
                // checkout abierto se confirma con Wompi que el pago no se resolvió sin que
                // llegara el webhook.
                Investment investment = contract.getInvestment();
                if (investment != null) {
                        PendingPaymentState state = reconcilePendingPayment(investment);
                        if (state == PendingPaymentState.PAID || state == PendingPaymentState.IN_PROCESS) {
                                log.info("[PLAN JOB] Recarga no vencida, el pago está {}: contractId={}", state, contractId);
                                return false;
                        }
                }
                commercialContractService.expireRecharge(contractId);
                return true;
        }

        private void requireNoLivePayment(Investment investment) {
                switch (reconcilePendingPayment(investment)) {
                        case PAID -> throw new RechargeAlreadyPaidException(
                                        "Esta recarga ya fue pagada, no se puede cancelar — el saldo ya está acreditado en su cuenta.");
                        case IN_PROCESS -> throw new BusinessException(
                                        "Hay un pago en proceso para esta recarga. Espere a que se resuelva antes de cancelarla.");
                        case NOT_STARTED, FAILED -> { }
                }
        }

        /** Resultado de conciliar con Wompi el checkout ya abierto de una recarga. */
        private enum PendingPaymentState {
                /** Wompi no tiene ninguna transacción: abrió el checkout y no pagó. */
                NOT_STARTED,
                /** Hay una transacción todavía sin resolver (p. ej. PSE esperando al banco). */
                IN_PROCESS,
                /** Aprobada — el saldo ya quedó acreditado (por el webhook o aquí mismo). */
                PAID,
                /** Rechazada, con error o anulada. */
                FAILED
        }

        /**
         * Consulta en Wompi qué pasó con el checkout de un Investment aún sin confirmar
         * y, si el webhook nunca llegó, aplica aquí el resultado (acreditar el saldo o
         * marcar el depósito como fallido).
         *
         * @throws BusinessException si no se puede hablar con Wompi. No se asume "no hay
         *         pago": con la pasarela caída no se sabe, y retomar o cancelar a ciegas
         *         puede terminar en doble cobro o en un pago sin contrato.
         */
        private PendingPaymentState reconcilePendingPayment(Investment investment) {
                if (Boolean.TRUE.equals(investment.getConfirmed())) {
                        return PendingPaymentState.PAID;
                }
                String reference = investment.getWompiReference();

                WompiTransactionData data;
                try {
                        data = wompiService.reconcileByReference(reference).orElse(null);
                } catch (RuntimeException e) {
                        log.error("[PLAN] No se pudo conciliar la recarga con Wompi: reference={}, error={}",
                                        reference, e.getMessage());
                        throw new BusinessException(
                                        "No pudimos verificar el estado de su pago con la pasarela. Intente de nuevo en unos minutos.");
                }

                if (data == null) {
                        return investment.getFailedAt() != null
                                        ? PendingPaymentState.FAILED : PendingPaymentState.NOT_STARTED;
                }
                if (!data.isTerminal()) {
                        return PendingPaymentState.IN_PROCESS;
                }
                if (data.isApproved()) {
                        log.warn("[PLAN] Wompi reporta APPROVED para reference={} pero el webhook nunca llegó. "
                                        + "Acreditando ahora.", reference);
                        WompiTransaction tx = wompiService.updateTransactionFromWebhook(
                                        data.getId(), reference, data.getStatus(), data.getCreatedAt(),
                                        Map.of("reconciled_from", "recharge_recovery"));
                        handleWompiResult(tx.getId());
                        return PendingPaymentState.PAID;
                }
                if (investment.getFailedAt() == null) {
                        WompiTransaction tx = wompiService.updateTransactionFromWebhook(
                                        data.getId(), reference, data.getStatus(), data.getCreatedAt(),
                                        Map.of("reconciled_from", "recharge_recovery"));
                        investment.fail(tx);
                        investmentRepository.save(investment);
                }
                return PendingPaymentState.FAILED;
        }

        /** Misma definición de "en curso" que CommercialContractRepository#findOpenRechargeContracts. */
        private boolean isOpenRecharge(CommercialContract contract) {
                return contract.getPurpose() == ContractPurpose.RECHARGE
                                && contract.getStatus() != ContractStatus.REJECTED
                                && contract.getStatus() != ContractStatus.CANCELLED
                                && (contract.getInvestment() == null
                                                || !Boolean.TRUE.equals(contract.getInvestment().getConfirmed()));
        }

        private Optional<OpenRechargeResponseDTO> findOpenRecharge(Long commercialId) {
                return commercialContractRepository.findOpenRechargeContracts(commercialId).stream()
                                .max(Comparator.comparing(CommercialContract::getGeneratedAt))
                                .map(this::toOpenRecharge);
        }

        private OpenRechargeResponseDTO toOpenRecharge(CommercialContract contract) {
                Investment investment = contract.getInvestment();

                NextAction nextAction;
                String message;
                if (contract.getStatus() != ContractStatus.SIGNED) {
                        nextAction = NextAction.SIGN;
                        message = "Tiene una recarga pendiente de firma — firme el otrosí para continuar, "
                                        + "o cancélela para solicitar otra.";
                } else if (investment != null && investment.getFailedAt() != null) {
                        nextAction = NextAction.RETRY_PAYMENT;
                        message = "El pago de su recarga fue rechazado — puede intentarlo de nuevo, "
                                        + "o cancelarla para solicitar otra.";
                } else {
                        nextAction = NextAction.PAY;
                        message = "Tiene una recarga firmada pendiente de pago — complete el pago, "
                                        + "o cancélela para solicitar otra.";
                }

                Long amountCents = contract.getAmountCentsSnapshot();
                Long vatAmountCents = investment != null ? investment.getVatAmountCents()
                                : amountCents != null ? Long.valueOf(vatFor(amountCents)) : null;

                return OpenRechargeResponseDTO.builder()
                                .contractId(contract.getId())
                                .status(contract.getStatus())
                                .nextAction(nextAction)
                                .message(message)
                                .amountPesos(centsToPesos(amountCents))
                                .vatAmountPesos(centsToPesos(vatAmountCents))
                                .totalToPayPesos(amountCents != null && vatAmountCents != null
                                                ? centsToPesos(amountCents + vatAmountCents) : null)
                                .generatedAt(contract.getGeneratedAt())
                                .signedAt(contract.getEsignatureSignedAt())
                                .expiresAt(contract.getGeneratedAt() != null
                                                ? contract.getGeneratedAt().plusHours(rechargeMaxAgeHours) : null)
                                .paymentAttempted(investment != null)
                                .build();
        }

        /**
         * Genera el checkout del abono requerido por un cambio de plan ya firmado
         * (PlanChangeRequest en PAYMENT_PENDING). Si el destino es BASIC crea una
         * Subscription; si es STANDARD/PREMIUM crea un Investment — en ambos casos
         * vinculado al contrato para que, al confirmarse el pago,
         * PlanServiceImpl#applyPlanChangeIfLinked aplique el cambio automáticamente.
         */
        @Override
        @Transactional
        public WompiCheckoutResponseDTO generatePlanChangeTopUpCheckout(Long requestId, CommercialDetails commercial) {
                PlanChangeRequest request = planChangeRequestRepository.findById(requestId)
                                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + requestId));

                if (!request.getCommercial().getId().equals(commercial.getId())) {
                        throw new IllegalArgumentException("Solicitud no encontrada: " + requestId);
                }
                if (request.getStatus() != com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus.PAYMENT_PENDING) {
                        throw new IllegalStateException("Esta solicitud no está pendiente de pago.");
                }

                CommercialContract contract = request.getContract();
                if (contract == null || contract.getStatus() != ContractStatus.SIGNED) {
                        throw new IllegalStateException("El contrato de cambio de plan debe estar firmado antes de pagar.");
                }
                if (contract.getInvestment() != null || contract.getSubscription() != null) {
                        throw new IllegalStateException("Esta solicitud ya generó un pago.");
                }

                Plan targetPlan = request.getToPlan();
                long amountCents = request.getRequiredTopUpAmountCents();
                long vatAmountCents = vatFor(amountCents);
                long totalAmountCents = amountCents + vatAmountCents;
                String idPart = commercial.getUser().getPublicId().toString().replace("-", "").substring(0, 12);

                WompiCheckoutRequestDTO.WompiCheckoutRequestDTOBuilder wompiRequestBuilder = WompiCheckoutRequestDTO.builder()
                                .amountInCents(totalAmountCents)
                                .customerEmail(commercial.getUser().getEmail())
                                .redirectUrl("http://verygana.com/empresario/plan/resultado");

                WompiCheckoutResponseDTO response;
                if (targetPlan.getCode() == PlanCode.BASIC) {
                        String reference = "VG-SUB-" + idPart + "-" + System.currentTimeMillis();
                        Subscription pending = createPendingSubscription(
                                        commercial, targetPlan, amountCents, vatAmountCents, reference);
                        contract.setSubscription(pending);
                        commercialContractRepository.save(contract);

                        response = wompiService.createCheckoutUrl(
                                        wompiRequestBuilder.reference(reference).build(),
                                        WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION);
                } else {
                        String reference = "VG-DEP-" + idPart + "-" + System.currentTimeMillis();
                        Investment pending = createPendingInvestment(
                                        commercial, targetPlan, amountCents, vatAmountCents, reference);
                        contract.setInvestment(pending);
                        commercialContractRepository.save(contract);

                        response = wompiService.createCheckoutUrl(
                                        wompiRequestBuilder.reference(reference).build(),
                                        WompiTransactionType.CHARGE_BUSINESS_DEPOSIT);
                }

                log.info("[PLAN CHANGE] Checkout de abono generado: requestId={}, targetPlan={}, base={}, vat={}, total={}",
                                requestId, targetPlan.getCode(), amountCents, vatAmountCents, totalAmountCents);

                return response;
        }

        private void activateInvestment(WompiTransaction wompiTx) {
                // 1. Lookup con lock pesimista — bloquea la fila hasta que esta transacción
                // haga commit, para que una segunda entrega concurrente del mismo webhook
                // espere aquí y no lea confirmed=false dos veces.
                Investment investment = investmentRepository
                                .findByWompiReferenceForUpdate(wompiTx.getReference())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Investment no encontrado para reference: " +
                                                                wompiTx.getReference()));

                // Idempotencia: si ya fue confirmado (por esta misma llamada en una entrega
                // anterior, o por una transacción concurrente que ya liberó el lock), no
                // repetir confirm()/registerDeposit()/distributeDeposit().
                if (Boolean.TRUE.equals(investment.getConfirmed())) {
                        log.info("[PLAN] Investment ya confirmado — entrega duplicada del webhook ignorada: reference={}",
                                        wompiTx.getReference());
                        return;
                }

                // 2. Confirmar el depósito (marca confirmed=true, guarda wompiTx)
                investment.confirm(wompiTx);
                investmentRepository.save(investment);

                // 2b. Umbral de Prosperidad (MP-05): solo si el plan del depósito tiene
                // multiplicador (STANDARD). Neto × 4, sin IVA, acumulativo sobre el Saldo.
                prosperityService.generateThreshold(investment);

                Wallet wallet = investment.getWallet();
                boolean wasExhausted = wallet.isExhausted();
                
                // 3. Calculo de deposito a la wallet (descontando comision para operaciones verygana y
                // fondo de fortalecimiento). Se usa depositAmountCents (sin IVA), NO
                // wompiTx.getAmountInCents() — ese último incluye el IVA adicional que el
                // empresario pagó y que no es presupuesto publicitario.
                BigDecimal amount = BigDecimal.valueOf(investment.getDepositAmountCents()) // long exacto
                                .multiply(BigDecimal.valueOf(treasuryConfig.getKeysReservePct())) // int exacto
                                .divide(BigDecimal.valueOf(100));

                // 4. Acreditar saldo — registerDeposit() recalcula el status y guarda el
                // monto como referencia para el umbral de aviso de saldo bajo.
                wallet.registerDeposit(amount.longValue());

                walletRepository.save(wallet);

                CommercialDetails commercial = wallet.getCommercial();

                // 5. Acreditar saldo nunca cambia el plan por sí solo — salvo que este
                // Investment sea el abono de activación del registro inicial (onboarding
                // en PAYMENT_PENDING, ver completeOnboardingIfPending) o el abono de un
                // cambio de plan explícito ya aprobado y firmado, en cuyo caso
                // applyPlanChangeIfLinked() lo detecta y aplica.
                boolean isInitialActivation = completeOnboardingIfPending(commercial, investment.getPlanAtDeposit());

                log.info("[PLAN] Inversión activada: commercialId={}, amount={}, " +
                                "plan={}, walletStatus={}",
                                commercial.getId(), wompiTx.getAmountInCents(),
                                investment.getPlanAtDeposit().getCode(), wallet.getStatus());

                // 6. Si el wallet estaba EXHAUSTED, reactivar todas las interacciones pausadas.
                // wasExhausted también es true en el primer depósito de una wallet recién
                // creada (balance arranca en 0) — eso no es una "reactivación", es la
                // activación inicial, así que se excluye para no mandar el correo de
                // "saldo restaurado" a alguien que nunca tuvo saldo.
                if (wasExhausted && !isInitialActivation) {
                        log.info("[PLAN] Wallet reactivado después de agotamiento: " +
                                        "commercialId={} — reactivando interacciones", commercial.getId());
                        investmentService.handleWalletReplenished(commercial.getId());
                        // TODO (fase siguiente): interactionService.reactivateAll(commercial);
                        // Este método buscará todas las interacciones del comercial con
                        // status=PAUSED_BY_BALANCE y las volverá a ACTIVE
                }

                // 7. Distribuir en tesorería — 60% KEYS_RESERVE / 10% FORTIFICATION / 30%
                // OPERATIONS sobre la base; el IVA (si lo hubo) va aparte a TAX_RESERVE
                treasuryService.distributeDeposit(
                                investment.getDepositAmountCents(),
                                investment.getVatAmountCents(),
                                commercial,
                                wompiTx.getId());

                applyPlanChangeIfLinked(investment.getId(), null);
        }

        /**
         * Si el Investment/Subscription recién confirmado es el abono de un cambio de
         * plan explícito ya firmado (contrato PLAN_CHANGE con esa referencia vinculada),
         * aplica el cambio ahora. No-op para cualquier otro pago (recarga normal,
         * renovación BASIC común, etc. — la mayoría de los casos).
         */
        private void applyPlanChangeIfLinked(Long investmentId, java.util.UUID subscriptionId) {
                var contractOpt = investmentId != null
                                ? commercialContractRepository.findByInvestment_Id(investmentId)
                                : commercialContractRepository.findBySubscription_Id(subscriptionId);

                // Un checkout abandonado sigue siendo pagable en Wompi después de cancelar o
                // vencer la recarga. El dinero entró, así que el saldo se acredita igual; se
                // deja rastro porque el otrosí de esa recarga ya no está vigente.
                contractOpt.filter(c -> c.getStatus() == ContractStatus.CANCELLED)
                                .ifPresent(c -> log.warn("[PLAN] Pago confirmado sobre un contrato CANCELLED: "
                                                + "contractId={}, purpose={}", c.getId(), c.getPurpose()));

                contractOpt.filter(c -> c.getPurpose() == ContractPurpose.PLAN_CHANGE)
                                .flatMap(c -> planChangeRequestRepository.findByContract_Id(c.getId()))
                                .ifPresent(request -> planChangeRequestService.applyIfPending(request.getId()));
        }

        /**
         * Si este pago era el de activación del registro comercial (onboarding en
         * PAYMENT_PENDING), lo completa y fija {@code activatedPlan} como plan vigente
         * del comercial — es la primera vez que el comercial tiene un plan, así que no
         * hay "cambio" que pasar por el flujo explícito de PlanChangeRequest. Para pagos
         * posteriores — renovación de BASIC, recarga de inversión — el onboarding ya
         * está COMPLETED y esto no hace nada (el plan no se toca).
         *
         * @return true si este pago era la activación inicial del registro.
         */
        private boolean completeOnboardingIfPending(CommercialDetails commercial, Plan activatedPlan) {
                return onboardingRepository.findByCommercialDetails_Id(commercial.getId())
                                .filter(o -> o.getCurrentStep() == OnboardingStep.PAYMENT_PENDING)
                                .map(onboarding -> {
                                        onboarding.setCurrentStep(OnboardingStep.COMPLETED);
                                        onboarding.setCompletedAt(ZonedDateTime.now());
                                        onboardingRepository.save(onboarding);

                                        commercial.setCurrentPlan(activatedPlan);
                                        commercialDetailsRepository.save(commercial);

                                        log.info("[PLAN] Onboarding completado tras pago de activación: commercialId={}, plan={}",
                                                        commercial.getId(), activatedPlan.getCode());
                                        return true;
                                })
                                .orElse(false);
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
                        CommercialDetails commercial = sub.getCommercial();
                        emailService.sendSubscriptionExpiredEmail(
                                        commercial.getUser().getEmail(), commercial.getCompanyName());
                });
        }

        /**
         * Recordatorios de renovación para suscripciones que vencen en 3 días.
         * Corre a las 2 PM Colombia (7 PM UTC).
         */
        @Scheduled(cron = "${subscription.reminder-cron:0 0 19 * * *}")
        @Transactional
        public void sendRenewalReminders() {
                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                subscriptionRepository
                                .findExpiringBetween(now, now.plusDays(3))
                                .stream()
                                // Ya se envió un recordatorio en este ciclo — no reenviar cada día dentro de la ventana.
                                .filter(sub -> sub.getRenewalReminderSentAt() == null)
                                .forEach(sub -> {
                                        log.info("[PLAN JOB] Recordatorio enviado: commercialId={}, dias={}",
                                                        sub.getCommercial().getId(), sub.daysRemaining());
                                        CommercialDetails commercial = sub.getCommercial();
                                        emailService.sendRenewalReminderEmail(
                                                        commercial.getUser().getEmail(), commercial.getCompanyName(),
                                                        sub.daysRemaining());
                                        sub.setRenewalReminderSentAt(now);
                                        subscriptionRepository.save(sub);
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

        /**
         * IVA (configurable, ver TreasuryConfig.vatPct) que el empresario paga
         * ADICIONAL sobre un depósito de inversión o una suscripción BASIC — a
         * diferencia de la comisión de venta, aquí el IVA se suma, no se extrae
         * (ej. base=$1.000.000, vatPct=19 → vat=$190.000, total=$1.190.000).
         */
        private long vatFor(long baseAmountCents) {
                return baseAmountCents * treasuryConfig.getVatPct() / 100;
        }

        private long resolveAmount(CommercialDetails commercial, Plan plan, Long amountCents) {
                // Un comercial con plan asignado solo puede renovar/recargar ese mismo plan por
                // esta vía — cambiar de plan siempre requiere una solicitud explícita (con
                // aprobación de VerYGana), nunca simplemente pagar bajo otro código de plan.
                if (commercial.getCurrentPlan() != null && commercial.getCurrentPlan().getCode() != plan.getCode()) {
                        throw new IllegalStateException(
                                        "Solo puede renovar su plan actual (" + commercial.getCurrentPlan().getCode() +
                                                        "). Para cambiar de plan, solicite un cambio de plan explícito.");
                }
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

        /**
         * Una vez completado el onboarding, una recarga STANDARD/PREMIUM debe pasar por
         * el flujo de contrato-primero-luego-pago ({@link #requestRecharge}) — este
         * endpoint legado queda solo para el primer pago (durante el onboarding, ya
         * cubierto por el Contrato Marco) y para la renovación mensual de BASIC.
         */
        private void requireLegacyPaymentAllowed(CommercialDetails commercial, PlanCode planCode) {
                if (planCode == PlanCode.BASIC) {
                        return;
                }
                boolean onboardingCompleted = onboardingRepository.findByCommercialDetails_Id(commercial.getId())
                                .map(o -> o.getCurrentStep() == OnboardingStep.COMPLETED)
                                .orElse(false);
                if (onboardingCompleted && commercial.getCurrentPlan() != null) {
                        throw new IllegalStateException(
                                        "Ya completó su registro — use la solicitud de recarga (con contrato) para depositar más saldo.");
                }
        }

        private void validateInvestmentAmount(Long amountCents, Plan plan) {
                if (amountCents == null || amountCents <= 0) {
                        throw new InvalidAmountException("El monto debe ser positivo.");
                }
                if (plan.getMinInvestmentCents() != null
                                && amountCents < plan.getMinInvestmentCents()) {
                        throw new InvalidAmountException(
                                        "Monto mínimo para " + plan.getCode() + ": $" +
                                                        centsToPesos(plan.getMinInvestmentCents()) + ".");
                }
                if (plan.getMaxInvestmentCents() != null
                                && amountCents > plan.getMaxInvestmentCents()) {
                        throw new InvalidAmountException(
                                        "Monto máximo para " + plan.getCode() + ": $" +
                                                        centsToPesos(plan.getMaxInvestmentCents()) + ".");
                }
        }

        private void handleFailedPayment(WompiTransaction wompiTx) {
                log.warn("[PLAN] Pago fallido: reference={}, status={}",
                                wompiTx.getReference(), wompiTx.getStatus());

                // Intentar marcar la Subscription o Investment como fallida
                subscriptionRepository.findByWompiReference(wompiTx.getReference())
                                .ifPresent(sub -> {
                                        sub.setStatus(SubscriptionStatus.PAYMENT_FAILED);
                                        subscriptionRepository.save(sub);
                                        CommercialDetails commercial = sub.getCommercial();
                                        emailService.sendPlanPaymentFailedEmail(
                                                        commercial.getUser().getEmail(), commercial.getCompanyName());
                                });

                investmentRepository.findByWompiReference(wompiTx.getReference())
                                .ifPresent(inv -> {
                                        inv.fail(wompiTx);
                                        investmentRepository.save(inv);
                                        CommercialDetails commercial = inv.getWallet().getCommercial();
                                        emailService.sendPlanPaymentFailedEmail(
                                                        commercial.getUser().getEmail(), commercial.getCompanyName());
                                });
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
                                        .budgetSuspended(true)
                                        .budgetDormant(false)
                                        .remainingBudgetCents(0L)
                                        .commissionRate(0)
                                        .canAdvertise(false)
                                        .canUseGames(false)
                                        .canUseSurveys(false)
                                        .canViewPerformanceMetrics(false)
                                        .canViewPageVisitMetrics(false)
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
                                        .budgetSuspended(false) // BASIC no tiene presupuesto publicitario
                                        .budgetDormant(false)
                                        .remainingBudgetCents(0L)
                                        .commissionRate(currentPlan.getSaleCommissionPct())
                                        .canAdvertise(currentPlan.getBoolFeature("CAN_ADVERTISE", false))
                                        .canUseGames(currentPlan.getBoolFeature("CAN_USE_GAMES", false))
                                        .canUseSurveys(currentPlan.getBoolFeature("CAN_USE_SURVEYS", false))
                                        .canViewPerformanceMetrics(currentPlan.getBoolFeature("CAN_VIEW_PERFORMANCE_METRICS", false))
                                        .canViewPageVisitMetrics(currentPlan.getBoolFeature("CAN_VIEW_PAGE_VISIT_METRICS", false))
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
                // hasActivePlan refleja "tiene plan contratado", no el saldo. El saldo
                // agotado se comunica vía budgetSuspended + walletStatus para que el
                // frontend bloquee solo la creación de activos nuevos, no todo.
                Wallet wallet = commercial.getWallet();
                String walletStatus = wallet != null ? wallet.getStatus().name() : "INACTIVE";
                long remainingBudgetCents = wallet != null ? wallet.getBalanceCents() : 0L;
                boolean budgetSuspended = wallet == null || wallet.isExhausted();
                boolean budgetDormant = budgetSuspended && wallet != null && wallet.getExhaustedSince() != null
                                && wallet.getExhaustedSince().isBefore(ZonedDateTime.now(ZoneOffset.UTC)
                                                .minusDays(effectivePlanResolver.resolveGracePeriodDays(currentPlan)));

                return EffectivePlanStateResponseDTO.builder()
                                .effectivePlan(currentPlan.getCode().name())
                                .hasActivePlan(true)
                                .budgetSuspended(budgetSuspended)
                                .budgetDormant(budgetDormant)
                                .remainingBudgetCents(remainingBudgetCents)
                                .commissionRate(currentPlan.getSaleCommissionPct())
                                .canAdvertise(currentPlan.getBoolFeature("CAN_ADVERTISE", false))
                                .canUseGames(currentPlan.getBoolFeature("CAN_USE_GAMES", false))
                                .canUseSurveys(currentPlan.getBoolFeature("CAN_USE_SURVEYS", false))
                                .canViewPerformanceMetrics(currentPlan.getBoolFeature("CAN_VIEW_PERFORMANCE_METRICS", false))
                                .canViewPageVisitMetrics(currentPlan.getBoolFeature("CAN_VIEW_PAGE_VISIT_METRICS", false))
                                .maxProducts(currentPlan.getIntFeature("MAX_PRODUCTS", 100))
                                .maxAds(currentPlan.getIntFeature("MAX_ADS", 0))
                                .maxBrandedGames(currentPlan.getIntFeature("MAX_BRANDED_GAMES", 0))
                                .maxSurveys(currentPlan.getIntFeature("MAX_SURVEYS", 0))
                                .maxKeysPct(currentPlan.getMaxKeysPct())
                                .subscriptionDaysRemaining(null)
                                .walletStatus(walletStatus)
                                .build();
        }

        @Override
        @Transactional(readOnly = true)
        public PlanCatalogResponseDTO getPlanCatalog(CommercialDetails commercial) {
                Plan currentPlan = commercial.getCurrentPlan();
                PlanCode currentPlanCode = currentPlan != null ? currentPlan.getCode() : null;

                List<PlanOptionDTO> plans = planRepository.findAllByActiveTrue().stream()
                                .sorted(Comparator.comparing(p -> p.getCode().ordinal()))
                                .map(p -> commercialOnboardingMapper.toPlanOptionDTO(p, false, p.getCode() == currentPlanCode))
                                .toList();

                return new PlanCatalogResponseDTO(currentPlanCode, plans);
        }

}
