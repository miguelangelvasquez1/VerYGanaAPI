package com.verygana2.services.finance;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.finance.plans.responses.PlanChangeBlockerDTO;
import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.event.ContractRejectedEvent;
import com.verygana2.event.ContractSignedEvent;
import com.verygana2.exceptions.BusinessException;
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
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;
import com.verygana2.services.plans.PlanChangeAssetValidator;

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
    private final NotificationService notificationService;
    private final PlanChangeAssetValidator planChangeAssetValidator;

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
                    "Para cambiar a BASIC su saldo publicitario debe estar en $0. Saldo actual: $"
                            + centsToPesos(walletBalanceCents(commercial)) + ".");
        }

        // El plan destino puede permitir menos activos (o ninguno) de los que el comercial
        // tiene activos ahora. Los activos no se pueden borrar: debe esperar a que finalicen
        // (o pedir su cancelación al soporte) antes de poder pedir el cambio. Aplica en
        // cualquier dirección: PREMIUM→STANDARD baja los máximos, STANDARD→PREMIUM quita los
        // productos, cualquiera→BASIC quita anuncios/juegos/encuestas.
        List<PlanChangeBlockerDTO> blockers = planChangeAssetValidator.findBlockers(commercialId, targetPlan);
        if (!blockers.isEmpty()) {
            throw new BusinessException("No puede cambiar al plan " + targetPlanCode
                    + " mientras tenga activos que exceden lo que ese plan permite. "
                    + blockers.stream().map(PlanChangeBlockerDTO::getMessage).collect(Collectors.joining(" "))
                    + " Si necesita cancelar activos antes de que finalicen, contacte al soporte de VerYGana.");
        }

        if (!planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercialId, OPEN_EXCLUDED_STATUSES).isEmpty()) {
            throw new BusinessException("Ya tiene una solicitud de cambio de plan en curso.");
        }
        if (!commercialContractRepository.findOpenRechargeContracts(commercialId).isEmpty()) {
            throw new BusinessException("Tiene una recarga en curso — resuélvala antes de solicitar un cambio de plan.");
        }

        long requiredTopUp = computeRequiredTopUp(targetPlan, intendedInvestmentAmountCents);

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
        long requiredTopUp = computeRequiredTopUp(targetPlan, intendedInvestmentAmountCents);

        boolean downgradeToBasic = targetPlanCode == PlanCode.BASIC && fromPlan != null && fromPlan.getCode() != PlanCode.BASIC;
        boolean balanceBlocksBasic = downgradeToBasic && balance != 0L;

        // Activos activos que no cabrían en el plan destino — deben finalizar antes de solicitar.
        List<PlanChangeBlockerDTO> blockers = planChangeAssetValidator.findBlockers(commercialId, targetPlan);

        boolean eligible = !balanceBlocksBasic && blockers.isEmpty();

        String message = buildPreviewMessage(targetPlanCode, downgradeToBasic, balanceBlocksBasic, balance, blockers);

        return new PlanChangePreviewResponseDTO(
                fromPlan != null ? fromPlan.getCode() : null,
                targetPlanCode,
                eligible,
                message,
                centsToPesos(requiredTopUp),
                centsToPesos(balance),
                targetPlan.getCode() == PlanCode.BASIC ? centsToPesos(targetPlan.getMonthlyPriceCents()) : null,
                targetPlan.getCode() != PlanCode.BASIC ? centsToPesos(targetPlan.getMinInvestmentCents()) : null,
                targetPlan.getCode() != PlanCode.BASIC ? centsToPesos(targetPlan.getMaxInvestmentCents()) : null,
                targetPlan.getSaleCommissionPct(),
                blockers);
    }

    /**
     * Arma el mensaje del preview. Si hay algo que impide el cambio (saldo &gt; $0 al bajar
     * a BASIC y/o activos que sobran) lo explica todo junto; si no, describe cuándo
     * aplicará el cambio.
     */
    private String buildPreviewMessage(PlanCode targetPlanCode, boolean downgradeToBasic,
            boolean balanceBlocksBasic, long balance, List<PlanChangeBlockerDTO> blockers) {

        if (balanceBlocksBasic || !blockers.isEmpty()) {
            StringBuilder sb = new StringBuilder("Antes de solicitar el cambio al plan ")
                    .append(targetPlanCode).append(": ");
            if (balanceBlocksBasic) {
                sb.append("deje su saldo publicitario en $0 (actual: $").append(centsToPesos(balance)).append("). ");
            }
            blockers.forEach(b -> sb.append(b.getMessage()).append(" "));
            if (!blockers.isEmpty()) {
                sb.append("Si necesita cancelar activos antes de que finalicen, contacte al soporte de VerYGana.");
            }
            return sb.toString().trim();
        }

        if (downgradeToBasic) {
            return "Su saldo está en $0 y sus activos caben en BASIC — puede continuar. "
                    + "El cambio aplicará una vez pague la tarifa mensual de BASIC tras firmar el otrosí.";
        }
        // El abono del cambio de plan no depende del saldo actual: siempre se paga el
        // monto a invertir en el plan destino (o su mínimo) para que el cambio aplique.
        return "El cambio se aplicará de inmediato una vez se confirme el pago del abono, después de firmar el otrosí.";
    }

    /** El preview del cambio de plan se muestra al comercial en pesos, no en centavos. */
    private static Long centsToPesos(Long cents) {
        return cents != null ? cents / 100 : null;
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

        // El otrosí ya generado queda huérfano si no se limpia: borra su PDF de R2 y marca
        // el contrato como CANCELLED.
        if (request.getContract() != null) {
            commercialContractService.cancelPlanChangeContract(request.getContract().getId());
        }

        request.setStatus(PlanChangeRequestStatus.CANCELLED);
        return planChangeRequestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanChangeRequest getCurrent(Long commercialId) {
        PlanChangeRequest open = planChangeRequestRepository
                .findByCommercial_IdAndStatusNotIn(commercialId, OPEN_EXCLUDED_STATUSES)
                .stream()
                .findFirst()
                .orElse(null);
        if (open != null) {
            return open;
        }
        // Un rechazo que el comercial todavía no dio por leído se sigue devolviendo (con
        // rejectionReason) para que el frontend muestre el motivo. Una vez que llama a
        // acknowledgeRejection, este método vuelve a responder null y puede crear otra.
        return planChangeRequestRepository
                .findFirstByCommercial_IdAndStatusAndRejectionAcknowledgedAtIsNullOrderByRequestedAtDesc(
                        commercialId, PlanChangeRequestStatus.REJECTED)
                .orElse(null);
    }

    @Override
    @Transactional
    public PlanChangeRequest acknowledgeRejection(Long commercialId, Long requestId) {
        PlanChangeRequest request = planChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada: " + requestId));

        if (!request.getCommercial().getId().equals(commercialId)) {
            throw new EntityNotFoundException("Solicitud no encontrada: " + requestId);
        }
        if (request.getStatus() != PlanChangeRequestStatus.REJECTED) {
            throw new ValidationException("Solo puede dar por leído el rechazo de una solicitud rechazada.");
        }

        if (request.getRejectionAcknowledgedAt() == null) {
            request.setRejectionAcknowledgedAt(ZonedDateTime.now());
            request = planChangeRequestRepository.save(request);
        }
        return request;
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

    /**
     * Reacciona al rechazo del contrato PLAN_CHANGE por parte de VerYGana: marca la
     * solicitud como REJECTED y copia el motivo, para que el comercial lo lea vía
     * {@code getCurrent} antes de poder abrir una nueva solicitud.
     */
    @EventListener
    @Transactional
    public void onContractRejected(ContractRejectedEvent event) {
        if (event.getPurpose() != ContractPurpose.PLAN_CHANGE) {
            return;
        }
        planChangeRequestRepository.findByContract_Id(event.getContractId()).ifPresent(request -> {
            if (OPEN_EXCLUDED_STATUSES.contains(request.getStatus())) {
                return;
            }
            request.setStatus(PlanChangeRequestStatus.REJECTED);
            request.setRejectionReason(event.getReason());
            planChangeRequestRepository.save(request);
            log.info("[PLAN CHANGE] Contrato rechazado: requestId={}, contractId={}",
                    request.getId(), event.getContractId());
        });
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

        notificationService.createInternalNotification(commercial.getUser().getId(),
                "Cambio de plan aplicado",
                "Tu plan cambió a " + request.getToPlan().getCode() + ".",
                Instant.now());

        log.info("[PLAN CHANGE] Aplicado: commercialId={}, newPlan={}", commercial.getId(), request.getToPlan().getCode());
    }

    /**
     * Abono que el comercial debe pagar para que el cambio de plan aplique. Es
     * <b>independiente del saldo actual del wallet y de saldos/abonos anteriores</b>:
     * un cambio de plan no "reutiliza" saldo ya depositado.
     * <ul>
     *   <li>Destino BASIC: la tarifa mensual del plan.</li>
     *   <li>Destino STANDARD/PREMIUM: el monto que el comercial quiere invertir en el
     *       nuevo plan, acotado al rango [min, max] del plan destino. Si no indica
     *       monto, se toma el mínimo del plan.</li>
     * </ul>
     */
    private long computeRequiredTopUp(Plan targetPlan, Long intendedInvestmentAmountCents) {
        if (targetPlan.getCode() == PlanCode.BASIC) {
            return targetPlan.getMonthlyPriceCents() != null ? targetPlan.getMonthlyPriceCents() : 0L;
        }
        long minInvestment = targetPlan.getMinInvestmentCents() != null ? targetPlan.getMinInvestmentCents() : 0L;
        long maxInvestment = targetPlan.getMaxInvestmentCents() != null ? targetPlan.getMaxInvestmentCents() : Long.MAX_VALUE;

        if (intendedInvestmentAmountCents == null) {
            return minInvestment;
        }
        if (intendedInvestmentAmountCents < minInvestment || intendedInvestmentAmountCents > maxInvestment) {
            throw new ValidationException(
                    "El monto a invertir para el plan " + targetPlan.getCode() + " debe estar entre $"
                            + centsToPesos(minInvestment) + " y $" + centsToPesos(maxInvestment) + ".");
        }
        return intendedInvestmentAmountCents;
    }

    private long walletBalanceCents(CommercialDetails commercial) {
        return commercial.getWallet() != null && commercial.getWallet().getBalanceCents() != null
                ? commercial.getWallet().getBalanceCents() : 0L;
    }
}
