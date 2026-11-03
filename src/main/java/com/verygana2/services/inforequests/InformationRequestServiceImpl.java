package com.verygana2.services.inforequests;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CancelRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CreateRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RespondRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ReviewRequest;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.inforequests.CommunicationChannel;
import com.verygana2.models.enums.inforequests.DeliveryStatus;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.enums.inforequests.RequestItemType;
import com.verygana2.models.enums.inforequests.RequestReviewResult;
import com.verygana2.models.inforequests.ComplianceAlert;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.models.inforequests.InformationRequestCommunication;
import com.verygana2.models.inforequests.InformationRequestItem;
import com.verygana2.models.inforequests.InformationRequestResponse;
import com.verygana2.models.inforequests.InformationRequestReview;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRepository;
import com.verygana2.repositories.inforequests.InformationRequestRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InformationRequestServiceImpl implements InformationRequestService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final List<CommercialDocumentStatus> CURRENT = List.of(CommercialDocumentStatus.VALIDATED);

    private final InformationRequestRepository requestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final CommercialDocumentRepository documentRepository;
    private final ComplianceAlertRepository alertRepository;
    private final NotificationService notificationService;
    private final UserIdResolver userIdResolver;

    // ==================== SISTEMA ====================

    @Override
    public InformationRequest openSystemRequest(SystemRequest command) {
        Optional<InformationRequest> open = requestRepository.findByOpenDedupKey(command.dedupKey());
        if (open.isPresent()) {
            return open.get();
        }
        CommercialDetails commercial = commercialDetailsRepository.findById(command.commercialId())
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + command.commercialId()));
        InformationRequest request = build(commercial, command.trigger(), command.ruleCode(), command.ruleVersion(),
                command.reason(), command.relationToOperation(), null, null, command.dedupKey(), command.items());
        InformationRequest saved = requestRepository.save(request);
        communicate(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDedupKey(String dedupKey) {
        return requestRepository.existsByDedupKey(dedupKey);
    }

    // ==================== EMPRESARIO ====================

    @Override
    @Transactional(readOnly = true)
    public List<RequestView> listForCommercial(Long commercialId) {
        UUID publicId = userIdResolver.toPublicId(commercialId);
        return requestRepository.findByCommercial_IdOrderByIssuedAtDesc(commercialId).stream()
                .map(r -> InformationRequestViews.request(r, publicId, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RequestView getForCommercial(Long commercialId, UUID requestPublicId) {
        return InformationRequestViews.request(getOwnedOrThrow(commercialId, requestPublicId),
                userIdResolver.toPublicId(commercialId), false);
    }

    @Override
    public RequestView respond(Long commercialId, UUID requestPublicId, RespondRequest dto) {
        InformationRequest request = getOwnedOrThrow(commercialId, requestPublicId);
        if (request.getStatus() != InformationRequestStatus.OPEN
                && request.getStatus() != InformationRequestStatus.OBSERVED) {
            throw new InvalidRequestException("Este requerimiento no está esperando una respuesta suya.");
        }
        String message = trimToNull(dto.getMessage());
        List<Long> documentIds = dto.getDocumentIds() == null ? List.of() : dto.getDocumentIds().stream().distinct().toList();
        if (message == null && documentIds.isEmpty()) {
            throw new InvalidRequestException("Escriba una aclaración o adjunte al menos un documento.");
        }
        for (Long documentId : documentIds) {
            CommercialDocument document = documentRepository.findByIdAndCommercial_Id(documentId, commercialId)
                    .orElseThrow(() -> new InvalidRequestException("El documento " + documentId + " no está en su expediente."));
            if (document.getStatus() != CommercialDocumentStatus.VALIDATED) {
                throw new InvalidRequestException("El documento " + documentId + " no está vigente en su expediente.");
            }
        }

        InformationRequestResponse response = new InformationRequestResponse();
        response.setRequest(request);
        response.setResponderUserId(commercialId);
        response.setMessage(message);
        response.getDocumentIds().addAll(documentIds);
        response.setReceivedAt(ZonedDateTime.now());
        request.getResponses().add(response);
        // Recibir la respuesta no cierra: queda a la espera de revisión.
        request.setStatus(InformationRequestStatus.RESPONDED);
        requestRepository.save(request);

        return InformationRequestViews.request(request, userIdResolver.toPublicId(commercialId), false);
    }

    // ==================== COMPLIANCE ====================

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RequestView> search(InformationRequestStatus status, UUID commercialPublicId, Pageable pageable) {
        Long commercialId = commercialPublicId == null ? null : userIdResolver.toInternalId(commercialPublicId);
        return PagedResponse.from(requestRepository.search(status, commercialId, pageable)
                .map(r -> InformationRequestViews.request(r, userIdResolver.toPublicId(r.getCommercial().getId()), true)));
    }

    @Override
    @Transactional(readOnly = true)
    public RequestView getForCompliance(UUID requestPublicId) {
        InformationRequest request = getOrThrow(requestPublicId);
        return InformationRequestViews.request(request, userIdResolver.toPublicId(request.getCommercial().getId()), true);
    }

    @Override
    public RequestView create(CreateRequest dto, Long issuerUserId) {
        Long commercialId = userIdResolver.toInternalId(dto.getCommercialPublicId());
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + dto.getCommercialPublicId()));
        for (ItemRequest item : dto.getItems()) {
            if (item.getItemType() == RequestItemType.DOCUMENT && item.getDocumentType() == null) {
                throw new InvalidRequestException("Indique qué tipo de documento se pide.");
            }
            if (item.getItemType() == RequestItemType.FIELD && trimToNull(item.getFieldPath()) == null) {
                throw new InvalidRequestException("Indique qué campo se pide actualizar.");
            }
        }

        String dedupKey = "MANUAL:" + commercialId + ":" + dto.getTrigger() + ":" + itemsSignature(dto.getItems());
        requestRepository.findByOpenDedupKey(dedupKey).ifPresent(existing -> {
            throw new InvalidRequestException("Ya hay un requerimiento abierto idéntico ("
                    + existing.getPublicId() + "). No se crea otro.");
        });

        ComplianceAlert alert = null;
        if (dto.getAlertId() != null) {
            alert = alertRepository.findById(dto.getAlertId())
                    .orElseThrow(() -> new EntityNotFoundException("Alerta no encontrada: " + dto.getAlertId()));
            if (!alert.getCommercial().getId().equals(commercialId)) {
                throw new InvalidRequestException("La alerta es de otro empresario.");
            }
        }

        InformationRequest request = build(commercial, dto.getTrigger(), null, null, dto.getReason().trim(),
                dto.getRelationToOperation().trim(), dto.getDueDate(), issuerUserId, dedupKey, dto.getItems());
        InformationRequest saved = requestRepository.save(request);

        if (alert != null) {
            alert.setRequest(saved);
            if (alert.getReviewedAt() == null) {
                alert.setReviewedAt(ZonedDateTime.now());
                alert.setReviewedByUserId(issuerUserId);
                alert.setReviewNotes("Se abrió el requerimiento " + saved.getPublicId());
            }
            alertRepository.save(alert);
        }

        communicate(saved);
        return InformationRequestViews.request(saved, dto.getCommercialPublicId(), true);
    }

    @Override
    public RequestView review(UUID requestPublicId, Long reviewerUserId, ReviewRequest dto) {
        InformationRequest request = getOrThrow(requestPublicId);
        if (request.getStatus() != InformationRequestStatus.RESPONDED) {
            throw new InvalidRequestException("Solo se revisa un requerimiento con una respuesta pendiente de revisión.");
        }
        String notes = trimToNull(dto.getNotes());
        if (dto.getResult() == RequestReviewResult.INSUFFICIENT && notes == null) {
            throw new InvalidRequestException("Indique qué falta: la observación se vincula al requerimiento original.");
        }

        InformationRequestReview review = new InformationRequestReview();
        review.setRequest(request);
        review.setReviewerUserId(reviewerUserId);
        review.setResult(dto.getResult());
        review.setNotes(notes);
        review.setReviewedAt(ZonedDateTime.now());
        request.getReviews().add(review);

        if (dto.getResult() == RequestReviewResult.SUFFICIENT) {
            request.close(InformationRequestStatus.CLOSED_SATISFIED, reviewerUserId, notes);
        } else {
            request.setStatus(InformationRequestStatus.OBSERVED);
        }
        requestRepository.save(request);

        if (dto.getResult() == RequestReviewResult.INSUFFICIENT) {
            notifyCommercial(request, "Tu respuesta tiene observaciones",
                    "Revisamos tu respuesta a un requerimiento de información y quedó una observación por resolver.");
        }
        return InformationRequestViews.request(request, userIdResolver.toPublicId(request.getCommercial().getId()), true);
    }

    @Override
    public RequestView cancel(UUID requestPublicId, Long userId, CancelRequest dto) {
        InformationRequest request = getOrThrow(requestPublicId);
        if (!request.getStatus().isOpen()) {
            throw new InvalidRequestException("El requerimiento ya está cerrado.");
        }
        request.close(InformationRequestStatus.CANCELLED, userId, dto.getReason().trim());
        requestRepository.save(request);
        return InformationRequestViews.request(request, userIdResolver.toPublicId(request.getCommercial().getId()), true);
    }

    // ==================== HELPERS ====================

    private InformationRequest build(CommercialDetails commercial,
            com.verygana2.models.enums.inforequests.InformationRequestTrigger trigger, String ruleCode,
            Integer ruleVersion, String reason, String relation, LocalDate dueDate, Long issuer, String dedupKey,
            List<ItemRequest> items) {
        InformationRequest request = new InformationRequest();
        request.setCommercial(commercial);
        request.setTrigger(trigger);
        request.setRuleCode(ruleCode);
        request.setRuleVersion(ruleVersion);
        request.setReason(reason);
        request.setRelationToOperation(relation);
        request.setDueDate(dueDate);
        request.setIssuedByUserId(issuer);
        request.setStatus(InformationRequestStatus.OPEN);
        request.setDedupKey(dedupKey);
        request.setOpenDedupKey(dedupKey);
        for (ItemRequest dto : items) {
            InformationRequestItem item = new InformationRequestItem();
            item.setRequest(request);
            item.setItemType(dto.getItemType());
            item.setDocumentType(dto.getDocumentType());
            item.setFieldPath(trimToNull(dto.getFieldPath()));
            item.setEstablishmentId(dto.getEstablishmentId());
            item.setDescription(dto.getDescription().trim());
            item.setAcceptableResponse(trimToNull(dto.getAcceptableResponse()));
            if (dto.getItemType() == RequestItemType.DOCUMENT && dto.getDocumentType() != null) {
                item.setLinkedDocumentId(currentAcceptedDocument(commercial.getId(), dto).orElse(null));
            }
            request.getItems().add(item);
        }
        return request;
    }

    /**
     * Si ya hay un documento de ese tipo vigente, aceptado y sin vencer, se vincula: el
     * revisor decide si basta, en lugar de exigir otra carga al empresario.
     */
    private Optional<Long> currentAcceptedDocument(Long commercialId, ItemRequest item) {
        LocalDate today = LocalDate.now(BOGOTA);
        return documentRepository.findByCommercial_IdAndStatusInOrderByUploadedAtDesc(commercialId, CURRENT).stream()
                .filter(d -> d.getDocumentType() == item.getDocumentType())
                .filter(d -> d.getReviewStatus() == CommercialDocumentReviewStatus.ACCEPTED)
                .filter(d -> d.getExpiresAt() == null || !d.getExpiresAt().isBefore(today))
                .map(CommercialDocument::getId)
                .findFirst();
    }

    private void communicate(InformationRequest request) {
        notifyCommercial(request, "Solicitud de información de VERYGANA",
                "Necesitamos que actualices o aclares información de tu expediente. Motivo: " + request.getReason());
    }

    /** Registra el envío por la plataforma. Una falla queda como FAILED, nunca como "no respondió". */
    private void notifyCommercial(InformationRequest request, String title, String message) {
        InformationRequestCommunication communication = new InformationRequestCommunication();
        communication.setRequest(request);
        communication.setChannel(CommunicationChannel.IN_APP);
        communication.setRecipientUserId(request.getCommercial().getId());
        communication.setSentAt(ZonedDateTime.now());
        try {
            notificationService.createInternalNotification(request.getCommercial().getId(), title, message, Instant.now());
            communication.setDeliveryStatus(DeliveryStatus.SENT);
        } catch (Exception e) {
            log.warn("No se pudo notificar el requerimiento {}: {}", request.getPublicId(), e.getMessage());
            communication.setDeliveryStatus(DeliveryStatus.FAILED);
            communication.setErrorDetail(truncate(e.getMessage(), 500));
        }
        request.getCommunications().add(communication);
        requestRepository.save(request);
    }

    private static String itemsSignature(List<ItemRequest> items) {
        return items.stream()
                .map(i -> i.getItemType() + "|" + i.getDocumentType() + "|" + trimToNull(i.getFieldPath()) + "|"
                        + i.getEstablishmentId())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(";"))
                .hashCode() + "";
    }

    private InformationRequest getOrThrow(UUID publicId) {
        return requestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Requerimiento no encontrado: " + publicId));
    }

    private InformationRequest getOwnedOrThrow(Long commercialId, UUID publicId) {
        return requestRepository.findByPublicIdAndCommercial_Id(publicId, commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Requerimiento no encontrado: " + publicId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
