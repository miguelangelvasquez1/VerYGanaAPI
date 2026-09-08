package com.verygana2.services.pqrs;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.pqrs.PqrsSlaProperties;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.exceptions.pqrsExceptions.PqrsAccessDeniedException;
import com.verygana2.mappers.pqrs.PqrsAssetMapper;
import com.verygana2.mappers.pqrs.PqrsMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.enums.pqrs.PqrsResolutionAction;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.repositories.pqrs.PqrsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemRefundService;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.services.interfaces.pqrs.PqrsService;
import com.verygana2.utils.audit.AuditLevel;
import com.verygana2.utils.audit.Auditable;
import com.verygana2.utils.pqrs.BusinessDayCalculator;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PqrsServiceImpl implements PqrsService {

    private final PqrsRepository pqrsRepository;
    private final UserRepository userRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PqrsAssignmentService pqrsAssignmentService;
    private final PqrsMapper pqrsMapper;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BusinessDayCalculator businessDayCalculator;
    private final PqrsSlaProperties pqrsSlaProperties;
    private final RequesterNameResolver requesterNameResolver;
    private final PurchaseItemRefundService purchaseItemRefundService;
    private final PqrsAssetService pqrsAssetService;
    private final PqrsAssetMapper pqrsAssetMapper;

    @Override
    @Auditable(action = "PQRS_SUBMIT", level = AuditLevel.INFO, category = "PQRS", description = "Usuario radica un PQRS")
    public PqrsResponseDTO createPqrs(CreatePqrsRequestDTO dto, Long requesterUserId) {
        return createAndDispatch(dto.getType(), dto.getSubject(), dto.getDescription(), null, null, requesterUserId,
                dto.getAssetIds());
    }

    @Override
    @Auditable(action = "PQRS_SUBMIT_MARKETPLACE", level = AuditLevel.INFO, category = "PQRS",
            description = "Comprador reporta un problema con un ítem de compra")
    public PqrsResponseDTO createPqrsForPurchaseItem(PurchaseItem item, MarketplaceIssueReason reason,
            String description, Long requesterUserId, List<Long> assetIds) {
        String subject = "Reclamo por compra #" + item.getPurchase().getId() + " (" + reason + ")";
        return createAndDispatch(PqrsType.RECLAMO, subject, description, item, reason, requesterUserId, assetIds);
    }

    private PqrsResponseDTO createAndDispatch(PqrsType type, String subject, String description,
            PurchaseItem purchaseItem, MarketplaceIssueReason reasonCode, Long requesterUserId,
            List<Long> assetIds) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + requesterUserId));

        ZonedDateTime dueDate = businessDayCalculator.addBusinessDays(
                ZonedDateTime.now(), pqrsSlaProperties.getSlaDaysFor(type));

        Optional<AdminDetails> assignedAdmin = pqrsAssignmentService.pickNextAdmin();

        Pqrs pqrs = Pqrs.builder()
                .type(type)
                .requester(requester)
                .assignedAdmin(assignedAdmin.orElse(null))
                .subject(subject)
                .description(description)
                .dueDate(dueDate)
                .purchaseItem(purchaseItem)
                .reasonCode(reasonCode)
                .build();

        Pqrs saved = pqrsRepository.save(pqrs);
        log.info("PQRS {} creado por userId={}, status={}", saved.getId(), requesterUserId, saved.getStatus());

        // Lista null/vacía es un no-op inmediato — la evidencia es opcional. Si
        // falla (dueño incorrecto, no confirmado, ya reclamado), la excepción
        // revierte toda la transacción, incluyendo el insert del Pqrs de arriba.
        // OJO: no hacer saved.setAssets(claimedAssets) — Pqrs.assets tiene
        // orphanRemoval=true, así que Hibernate ya envolvió esa colección en un
        // PersistentBag propio de "saved"; reemplazar la referencia por una
        // lista distinta (la que devuelve el repositorio) hace que esa
        // colección quede "huérfana" de su entidad dueña y Hibernate lanza
        // JpaSystemException al hacer flush. Basta con construir la respuesta
        // directamente desde claimedAssets, sin tocar el campo de la entidad.
        List<PqrsAsset> claimedAssets = pqrsAssetService.validateAndClaimAssets(assetIds, requesterUserId, saved);

        if (purchaseItem != null) {
            purchaseItem.enterReview();
            purchaseItemRepository.save(purchaseItem);
        }

        assignedAdmin.ifPresent(admin -> notifyAdminOfAssignment(saved, admin));

        emailService.sendPqrsReceivedConfirmation(requester.getEmail(), requesterNameResolver.resolve(requester),
                saved.getBased(), saved.getType(), saved.getDueDate());

        PqrsResponseDTO dto = pqrsMapper.toResponseDTO(saved);
        dto.setAssets(mapAssetsWithViewUrl(claimedAssets));
        return dto;
    }

    @Override
    public PagedResponse<PqrsResponseDTO> getMyPqrs(Long requesterUserId, Pageable pageable) {
        return PagedResponse.from(pqrsRepository.findByRequesterId(requesterUserId, pageable)
                .map(this::toResponseDTOWithAssets));
    }

    @Override
    public PqrsResponseDTO getMyPqrsDetail(Long pqrsId, Long requesterUserId) {
        Pqrs pqrs = pqrsRepository.findById(pqrsId)
                .orElseThrow(() -> new EntityNotFoundException("PQRS not found: " + pqrsId));

        if (!pqrs.getRequester().getId().equals(requesterUserId)) {
            throw new EntityNotFoundException("PQRS not found: " + pqrsId);
        }

        return toResponseDTOWithAssets(pqrs);
    }

    @Override
    public PagedResponse<PqrsAdminDetailDTO> getAssignedPqrs(Long adminUserId, PqrsStatus status, PqrsType type,
            Pageable pageable) {
        return PagedResponse.from(pqrsRepository.findByAssignedAdminWithFilters(adminUserId, status, type, pageable)
                .map(this::toAdminDetailDTOWithAssets));
    }

    @Override
    public PqrsAdminDetailDTO getPqrsDetailForAdmin(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);
        return toAdminDetailDTOWithAssets(pqrs);
    }

    private PqrsResponseDTO toResponseDTOWithAssets(Pqrs pqrs) {
        PqrsResponseDTO dto = pqrsMapper.toResponseDTO(pqrs);
        dto.setAssets(mapAssetsWithViewUrl(pqrs));
        return dto;
    }

    private PqrsAdminDetailDTO toAdminDetailDTOWithAssets(Pqrs pqrs) {
        PqrsAdminDetailDTO dto = pqrsMapper.toAdminDetailDTO(pqrs);
        dto.setAssets(mapAssetsWithViewUrl(pqrs));
        return dto;
    }

    private List<PqrsAssetResponseDTO> mapAssetsWithViewUrl(Pqrs pqrs) {
        return mapAssetsWithViewUrl(pqrs.getAssets());
    }

    // viewUrl ya no es una URL prefirmada de R2 (rota por CORS del bucket) —
    // PqrsAssetMapper.toResponseDTO la arma como ruta propia del backend
    // (/pqrs/assets/{id}/view), cómputo puro, sin llamar a R2Service.
    private List<PqrsAssetResponseDTO> mapAssetsWithViewUrl(List<PqrsAsset> assets) {
        return assets.stream().map(pqrsAssetMapper::toResponseDTO).toList();
    }

    @Override
    @Auditable(action = "PQRS_REVIEW", level = AuditLevel.INFO, category = "PQRS", description = "Admin marca un PQRS en revisión")
    public void markUnderReview(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);

        if (!pqrs.canBeReviewed()) {
            throw new ValidationException("PQRS cannot be reviewed from status: " + pqrs.getStatus());
        }

        pqrs.setStatus(PqrsStatus.EN_REVISION);
        pqrsRepository.save(pqrs);
    }

    @Override
    @Auditable(action = "PQRS_RESPOND", level = AuditLevel.INFO, category = "PQRS", description = "Admin resuelve un PQRS")
    public void respondToPqrs(Long pqrsId, RespondPqrsRequestDTO dto, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);

        if (!pqrs.canBeResolved()) {
            throw new ValidationException("PQRS cannot be resolved from status: " + pqrs.getStatus());
        }

        boolean awaitingCashRefundPayment = false;

        if (pqrs.getPurchaseItem() != null) {
            if (dto.getAction() == null) {
                throw new ValidationException(
                        "Action (DISMISS/REFUND) is required to resolve a marketplace-linked PQRS");
            }

            PurchaseItem item = pqrs.getPurchaseItem();

            if (dto.getAction() == PqrsResolutionAction.DISMISS) {
                // El admin comprobó que el reclamo no procedía: el ítem retoma
                // exactamente el status que tenía antes de entrar en IN_REVIEW.
                item.exitReviewDismissed();
                purchaseItemRepository.save(item);
            } else if (dto.getAction() == PqrsResolutionAction.REFUND) {
                PurchaseItemCashRefund cashRefund = purchaseItemRefundService.refund(
                        item, pqrs.getReasonCode(), pqrs);
                // Si hubo porción en efectivo, el reembolso queda pendiente de pago
                // manual y el PQRS no se cierra todavía — se resuelve recién cuando
                // el admin confirma el pago (CashRefundServiceImpl.markPaid), para
                // que todo el flujo quede dentro del mismo PQRS. El ítem también se
                // queda IN_REVIEW hasta ese momento (ver PurchaseItemRefundServiceImpl.refund).
                awaitingCashRefundPayment = cashRefund != null;
            }
            // Persistido (no solo el campo transitorio del request) para que el
            // frontend, leyendo el PQRS ya resuelto, sepa si debe mostrarle al
            // comprador el formulario de datos bancarios.
            pqrs.setAction(dto.getAction());
        }

        pqrs.setResponse(dto.getResponse());

        if (awaitingCashRefundPayment) {
            pqrs.setStatus(PqrsStatus.PENDIENTE_PAGO_REEMBOLSO);
            pqrsRepository.save(pqrs);
            log.info("PQRS {} en espera de pago manual del reembolso, no se resuelve todavía", pqrs.getId());
            return;
        }

        pqrs.setStatus(PqrsStatus.RESUELTA);
        pqrs.setResolvedAt(ZonedDateTime.now());
        Pqrs saved = pqrsRepository.save(pqrs);

        User requester = saved.getRequester();
        notificationService.createInternalNotification(
                requester.getId(),
                "Tu PQRS fue resuelto",
                "Radicado " + saved.getBased() + ": " + dto.getResponse(),
                Instant.now());

        emailService.sendPqrsResolved(requester.getEmail(), requesterNameResolver.resolve(requester),
                saved.getBased(), dto.getResponse());
    }

    private Pqrs loadOwnedByAdmin(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = pqrsRepository.findById(pqrsId)
                .orElseThrow(() -> new EntityNotFoundException("PQRS not found: " + pqrsId));

        if (pqrs.getAssignedAdmin() == null || !pqrs.getAssignedAdmin().getUser().getId().equals(adminUserId)) {
            throw new PqrsAccessDeniedException("Este PQRS no está asignado a este administrador");
        }

        return pqrs;
    }

    @Override
    public void retryPendingAssignments() {
        var pending = pqrsRepository.findByStatus(PqrsStatus.PENDIENTE_ASIGNACION);
        if (pending.isEmpty()) return;

        log.info("Reintentando asignación de {} PQRS pendientes", pending.size());
        for (Pqrs pqrs : pending) {
            pqrsAssignmentService.pickNextAdmin().ifPresent(admin -> {
                pqrs.setAssignedAdmin(admin);
                pqrs.setStatus(PqrsStatus.RECIBIDA);
                Pqrs saved = pqrsRepository.save(pqrs);
                notifyAdminOfAssignment(saved, admin);
            });
        }
    }

    @Override
    public void sendSlaAlerts(int daysBeforeDueDateToAlert) {
        ZonedDateTime alertThreshold = ZonedDateTime.now().plusDays(daysBeforeDueDateToAlert);
        var atRisk = pqrsRepository.findByStatusInAndDueDateBefore(
                java.util.List.of(PqrsStatus.RECIBIDA, PqrsStatus.EN_REVISION), alertThreshold);

        for (Pqrs pqrs : atRisk) {
            AdminDetails admin = pqrs.getAssignedAdmin();
            if (admin == null) continue;

            emailService.sendPqrsSlaAlert(
                    admin.getUser().getEmail(),
                    requesterNameResolver.resolve(admin.getUser()),
                    pqrs.getBased(),
                    pqrs.getDueDate());
        }
    }

    private void notifyAdminOfAssignment(Pqrs pqrs, AdminDetails admin) {
        notificationService.createInternalNotification(
                admin.getUser().getId(),
                "Nuevo PQRS asignado",
                "Radicado " + pqrs.getBased() + ": " + pqrs.getSubject(),
                Instant.now());

        emailService.sendPqrsAssignedToAdmin(
                admin.getUser().getEmail(),
                requesterNameResolver.resolve(admin.getUser()),
                pqrs.getBased(),
                pqrs.getSubject(),
                pqrs.getDueDate());
    }
}
