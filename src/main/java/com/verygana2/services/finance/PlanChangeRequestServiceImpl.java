package com.verygana2.services.finance;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.event.ContractSignedEvent;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cambio de plan explícito. A diferencia de la recarga, SIEMPRE pasa por el
 * pipeline completo de revisión (negocio -> VerYGana -> firma) reutilizando
 * {@link CommercialContractService} — no inventa una segunda máquina de estados,
 * solo reacciona al evento de firma para aplicar el cambio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanChangeRequestServiceImpl implements PlanChangeRequestService {

    private static final List<PlanChangeRequestStatus> OPEN_EXCLUDED_STATUSES = List.of(
            PlanChangeRequestStatus.APPLIED, PlanChangeRequestStatus.REJECTED, PlanChangeRequestStatus.CANCELLED);

    private final PlanChangeRequestRepository planChangeRequestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final PlanRepository planRepository;
    private final CommercialContractService commercialContractService;
    private final CommercialContractRepository commercialContractRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public PlanChangeRequest requestPlanChange(Long commercialId, PlanCode targetPlanCode, Long intendedInvestmentAmountCents) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Comercial no encontrado: " + commercialId));

        Plan targetPlan = planRepository.findByCodeAndActiveTrue(targetPlanCode)
                .orElseThrow(() -> new ValidationException("Plan no encontrado o inactivo: " + targetPlanCode));

        Plan fromPlan = commercial.getCurrentPlan();
        if (fromPlan != null && fromPlan.getCode() == targetPlanCode) {
            throw new ValidationException(
                    "Ya tiene el plan " + targetPlanCode + " — para depositar más saldo use la recarga, no un cambio de plan.");
        }

        // Bajar de STANDARD/PREMIUM a BASIC solo se permite con saldo publicitario en $0 —
        // evita dejar dinero "atrapado" en un plan que ya no puede seguir consumiéndolo.
        if (targetPlanCode == PlanCode.BASIC && fromPlan != null && fromPlan.getCode() != PlanCode.BASIC
                && walletBalanceCents(commercial) != 0L) {
            throw new ValidationException(
                    "Para cambiar a BASIC su saldo publicitario debe estar en $0. Saldo actual: "
                            + walletBalanceCents(commercial) + " centavos.");
        }

        if (!planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercialId, OPEN_EXCLUDED_STATUSES).isEmpty()) {
            throw new ValidationException("Ya tiene una solicitud de cambio de plan en curso.");
        }
        if (!commercialContractRepository.findOpenRechargeContracts(commercialId).isEmpty()) {
            throw new ValidationException("Tiene una recarga en curso — resuélvala antes de solicitar un cambio de plan.");
        }

        long requiredTopUp = computeRequiredTopUp(commercial, targetPlan, intendedInvestmentAmountCents);

        PlanChangeRequest request = new PlanChangeRequest();
        request.setCommercial(commercial);
        request.setFromPlan(fromPlan);
        request.setToPlan(targetPlan);
        request.setRequestedInvestmentAmountCents(intendedInvestmentAmountCents);
        request.setRequiredTopUpAmountCents(requiredTopUp);
        request.setStatus(PlanChangeRequestStatus.REQUESTED);
        request = planChangeRequestRepository.save(request);

        ContractSummaryResponseDTO contractSummary = commercialContractService.generateFor(
                commercial, ContractPurpose.PLAN_CHANGE, null, targetPlan);

        commercialContractRepository.findById(contractSummary.getContractId()).ifPresent(request::setContract);
        request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
        request = planChangeRequestRepository.save(request);

        log.info("[PLAN CHANGE] Solicitud creada: commercialId={}, from={}, to={}, requiredTopUp={}",
                commercialId, fromPlan != null ? fromPlan.getCode() : null, targetPlanCode, requiredTopUp);

        return request;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanChangePreviewResponseDTO previewPlanChange(Long commercialId, PlanCode targetPlanCode, Long intendedInvestmentAmountCents) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Comercial no encontrado: " + commercialId));

        Plan targetPlan = planRepository.findByCodeAndActiveTrue(targetPlanCode)
                .orElseThrow(() -> new ValidationException("Plan no encontrado o inactivo: " + targetPlanCode));

        Plan fromPlan = commercial.getCurrentPlan();
        long balance = walletBalanceCents(commercial);
        long requiredTopUp = computeRequiredTopUp(commercial, targetPlan, intendedInvestmentAmountCents);

        boolean downgradeToBasic = targetPlanCode == PlanCode.BASIC && fromPlan != null && fromPlan.getCode() != PlanCode.BASIC;
        boolean eligible = !downgradeToBasic || balance == 0L;

        String message;
        if (downgradeToBasic) {
            message = eligible
                    ? "Su saldo está en $0 — puede continuar. El cambio aplicará una vez pague la tarifa mensual de BASIC tras firmar el otrosí."
                    : "Para cambiar a BASIC su saldo publicitario debe estar en $0 antes de solicitar el cambio. Saldo actual: "
                            + balance + " centavos.";
        } else if (fromPlan == null || fromPlan.getCode() == PlanCode.BASIC) {
            message = "El cambio se aplicará de inmediato una vez se confirme el pago del abono, después de firmar el otrosí.";
        } else if (requiredTopUp > 0) {
            message = "El cambio se aplicará de inmediato una vez se confirme el pago del abono adicional, después de firmar el otrosí.";
        } else {
            message = "Su saldo actual ya cubre el nuevo plan — el cambio aplicará en cuanto se firme el otrosí, sin pago adicional.";
        }

        return new PlanChangePreviewResponseDTO(
                fromPlan != null ? fromPlan.getCode() : null,
                targetPlanCode,
                eligible,
                message,
                requiredTopUp,
                balance,
                targetPlan.getCode() == PlanCode.BASIC ? targetPlan.getMonthlyPriceCents() : null,
                targetPlan.getCode() != PlanCode.BASIC ? targetPlan.getMinInvestmentCents() : null,
                targetPlan.getCode() != PlanCode.BASIC ? targetPlan.getMaxInvestmentCents() : null,
                targetPlan.getSaleCommissionPct());
    }

    @Override
    @Transactional
    public PlanChangeRequest cancelPlanChangeRequest(Long commercialId, Long requestId) {
        PlanChangeRequest request = planChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada: " + requestId));

        if (!request.getCommercial().getId().equals(commercialId)) {
            throw new EntityNotFoundException("Solicitud no encontrada: " + requestId);
        }
        if (request.getStatus() != PlanChangeRequestStatus.REQUESTED
                && request.getStatus() != PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW) {
            throw new ValidationException("Solo puede cancelar la solicitud antes de que el contrato sea firmado.");
        }

        request.setStatus(PlanChangeRequestStatus.CANCELLED);
        return planChangeRequestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanChangeRequest getCurrent(Long commercialId) {
        return planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercialId, OPEN_EXCLUDED_STATUSES)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanChangeRequest> listPendingReview() {
        return planChangeRequestRepository.findByStatusNotIn(OPEN_EXCLUDED_STATUSES);
    }

    /**
     * Reacciona a la firma de un contrato PLAN_CHANGE: si no requiere abono adicional
     * aplica el cambio de inmediato; si sí, queda en PAYMENT_PENDING a la espera de que
     * el comercial complete el pago del abono.
     */
    @EventListener
    @Transactional
    public void onContractSigned(ContractSignedEvent event) {
        if (event.getPurpose() != ContractPurpose.PLAN_CHANGE) {
            return;
        }
        planChangeRequestRepository.findByContract_Id(event.getContractId()).ifPresent(this::applyAfterSignature);
    }

    @Override
    @Transactional
    public void applyIfPending(Long planChangeRequestId) {
        planChangeRequestRepository.findById(planChangeRequestId)
                .filter(r -> r.getStatus() == PlanChangeRequestStatus.PAYMENT_PENDING)
                .ifPresent(this::applyPlanChange);
    }

    private void applyAfterSignature(PlanChangeRequest request) {
        if (request.getRequiredTopUpAmountCents() == null || request.getRequiredTopUpAmountCents() <= 0) {
            applyPlanChange(request);
        } else {
            request.setStatus(PlanChangeRequestStatus.PAYMENT_PENDING);
            planChangeRequestRepository.save(request);
            log.info("[PLAN CHANGE] Contrato firmado, pendiente de abono: requestId={}, topUp={}",
                    request.getId(), request.getRequiredTopUpAmountCents());
        }
    }

    private void applyPlanChange(PlanChangeRequest request) {
        CommercialDetails commercial = request.getCommercial();
        commercial.setCurrentPlan(request.getToPlan());
        commercialDetailsRepository.save(commercial);

        request.setStatus(PlanChangeRequestStatus.APPLIED);
        request.setAppliedAt(ZonedDateTime.now());
        planChangeRequestRepository.save(request);

        emailService.sendBudgetReplenishedEmail(commercial.getUser().getEmail(), commercial.getCompanyName());
        notificationService.createInternalNotification(commercial.getUser().getId(),
                "Cambio de plan aplicado",
                "Tu plan cambió a " + request.getToPlan().getCode() + ".",
                Instant.now());

        log.info("[PLAN CHANGE] Aplicado: commercialId={}, newPlan={}", commercial.getId(), request.getToPlan().getCode());
    }

    private long computeRequiredTopUp(CommercialDetails commercial, Plan targetPlan, Long intendedInvestmentAmountCents) {
        if (targetPlan.getCode() == PlanCode.BASIC) {
            return targetPlan.getMonthlyPriceCents() != null ? targetPlan.getMonthlyPriceCents() : 0L;
        }
        long minRequired = targetPlan.getMinInvestmentCents() != null ? targetPlan.getMinInvestmentCents() : 0L;
        return Math.max(0L, minRequired - walletBalanceCents(commercial));
    }

    private long walletBalanceCents(CommercialDetails commercial) {
        return commercial.getWallet() != null && commercial.getWallet().getBalanceCents() != null
                ? commercial.getWallet().getBalanceCents() : 0L;
    }
}
