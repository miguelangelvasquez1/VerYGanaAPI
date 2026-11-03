package com.verygana2.services.commercial;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.user.commercial.expediente.DocumentReviewRequestDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentUploadRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.commercial.CommercialExpedienteDocumentService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialExpedienteDocumentServiceImpl implements CommercialExpedienteDocumentService {

    private static final Set<SupportedMimeType> ALLOWED_MIME_TYPES = Set.of(
            SupportedMimeType.APPLICATION_PDF, SupportedMimeType.IMAGE_JPEG, SupportedMimeType.IMAGE_PNG);
    private static final String OBJECT_KEY_PREFIX = "commercial-documents/";
    private static final List<CommercialDocumentStatus> EXPEDIENTE_STATUSES = List.of(
            CommercialDocumentStatus.VALIDATED, CommercialDocumentStatus.SUPERSEDED);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int DOWNLOAD_URL_SECONDS = 300;

    private final CommercialDocumentRepository documentRepository;
    private final CommercialOnboardingRepository onboardingRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final R2Service r2Service;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${commercial.onboarding.documents.max-size-bytes}")
    private long maxSizeBytes;

    @Override
    @Transactional(readOnly = true)
    public List<ExpedienteDocumentDTO> listForCommercial(Long commercialId) {
        return documentRepository.findByCommercial_IdAndStatusInOrderByUploadedAtDesc(commercialId, EXPEDIENTE_STATUSES)
                .stream().map(this::toDTO).toList();
    }

    @Override
    public DocumentUploadPermissionDTO prepareUpload(Long commercialId, ExpedienteDocumentUploadRequestDTO dto) {
        CommercialOnboarding onboarding = onboardingRepository.findByCommercialDetails_Id(commercialId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No existe un proceso de onboarding comercial para userId: " + commercialId,
                        CommercialOnboarding.class));
        // Durante el onboarding los documentos se cargan por /commercials/onboarding/documents.
        if (onboarding.getCurrentStep() != OnboardingStep.COMPLETED) {
            throw new InvalidRequestException(
                    "El expediente se actualiza después de terminar el registro. "
                            + "Mientras tanto, cargue los documentos desde el paso de documentos del registro.");
        }

        if (dto.getSizeBytes() > maxSizeBytes) {
            throw new ValidationException(
                    "Archivo muy grande. Máximo permitido: " + (maxSizeBytes / 1024 / 1024) + " MB");
        }
        requireAllowedMime(dto.getContentType());

        CommercialDocument replaced = null;
        if (dto.getReplacesDocumentId() != null) {
            replaced = getOwnedOrThrow(dto.getReplacesDocumentId(), commercialId);
            requireReplaceable(replaced, dto);
        }

        String objectKey = OBJECT_KEY_PREFIX + onboarding.getId() + "/" + UUID.randomUUID();

        CommercialDocument document = new CommercialDocument();
        document.setOnboarding(onboarding);
        document.setCommercial(onboarding.getCommercialDetails());
        document.setDocumentType(dto.getDocumentType());
        document.setObjectKey(objectKey);
        document.setOriginalFileName(dto.getOriginalFileName());
        document.setSizeBytes(dto.getSizeBytes());
        document.setStatus(CommercialDocumentStatus.PENDING);
        document.setReplacesDocument(replaced);
        CommercialDocument saved = documentRepository.save(document);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, dto.getContentType());
        return new DocumentUploadPermissionDTO(saved.getId(), permission);
    }

    @Override
    public ExpedienteDocumentDTO confirmUpload(Long commercialId, Long documentId) {
        CommercialDocument document = getOwnedOrThrow(documentId, commercialId);
        if (document.getStatus() != CommercialDocumentStatus.PENDING) {
            throw new InvalidRequestException("Este documento ya fue confirmado o descartado.");
        }

        // Rechaza archivos vacíos o de tipo real distinto al declarado: subir "cualquier
        // archivo" no cuenta como aportar un documento.
        SupportedMimeType realMime = r2Service.validateUploadedObject(
                true, document.getObjectKey(), document.getSizeBytes(), maxSizeBytes, ALLOWED_MIME_TYPES);
        document.setMimeType(realMime);
        document.setStatus(CommercialDocumentStatus.VALIDATED);

        CommercialDocument replaced = document.getReplacesDocument();
        if (replaced != null) {
            // Otra carga pudo haberlo reemplazado entre prepare y confirm.
            if (replaced.getStatus() != CommercialDocumentStatus.VALIDATED) {
                throw new InvalidRequestException(
                        "El documento que intenta reemplazar ya no está vigente. Recargue el expediente.");
            }
            replaced.setStatus(CommercialDocumentStatus.SUPERSEDED);
            replaced.setSupersededAt(ZonedDateTime.now());
            documentRepository.save(replaced);
        }
        documentRepository.save(document);

        Map<String, Object> data = new HashMap<>();
        data.put("documentId", document.getId());
        data.put("documentType", document.getDocumentType().name());
        data.put("replacesDocumentId", replaced != null ? replaced.getId() : null);
        publishAudit(commercialId, "COMMERCIAL_EXPEDIENTE_DOCUMENT_UPLOADED",
                "Comercial cargó " + document.getDocumentType() + " a su expediente"
                        + (replaced != null ? " reemplazando el documento " + replaced.getId() + "." : "."),
                data);

        return toDTO(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpedienteDocumentDTO> listForCompliance(UUID commercialPublicId) {
        CommercialDetails commercial = commercialDetailsRepository.findByPublicId(commercialPublicId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Commercial with public id: " + commercialPublicId + " not found"));
        return listForCommercial(commercial.getId());
    }

    @Override
    public ExpedienteDocumentDTO review(Long documentId, Long reviewerUserId, DocumentReviewRequestDTO dto) {
        CommercialDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Documento no encontrado: " + documentId, CommercialDocument.class));
        if (document.getStatus() != CommercialDocumentStatus.VALIDATED) {
            throw new InvalidRequestException("Solo se revisan documentos vigentes del expediente.");
        }
        CommercialDetails commercial = document.getCommercial();
        if (commercial == null) {
            throw new InvalidRequestException("El documento no está asociado a un expediente.");
        }

        CommercialDocumentReviewStatus result = dto.getResult();
        if (result == CommercialDocumentReviewStatus.NOT_REVIEWED) {
            throw new InvalidRequestException("El resultado debe ser ACCEPTED, OBSERVED o REJECTED.");
        }
        if (result != CommercialDocumentReviewStatus.ACCEPTED && isBlank(dto.getNotes())) {
            throw new InvalidRequestException("Indique la observación o el motivo del rechazo.");
        }
        if (dto.getIssuedAt() != null && dto.getExpiresAt() != null && dto.getExpiresAt().isBefore(dto.getIssuedAt())) {
            throw new InvalidRequestException("La fecha de vencimiento no puede ser anterior a la de expedición.");
        }
        if (result == CommercialDocumentReviewStatus.ACCEPTED
                && DocumentHolderMatcher.requiresHolder(document.getDocumentType())) {
            if (isBlank(dto.getHolderIdentification())) {
                throw new InvalidRequestException(
                        "Para aceptar este documento indique el NIT o la cédula del titular.");
            }
            if (!DocumentHolderMatcher.matches(document.getDocumentType(), dto.getHolderIdentification(), commercial)) {
                throw new InvalidRequestException(
                        "El titular del documento no corresponde al empresario. Márquelo como OBSERVED o REJECTED.");
            }
        }

        document.setReviewStatus(result);
        document.setHolderIdentification(trimToNull(dto.getHolderIdentification()));
        document.setIssuedAt(dto.getIssuedAt());
        document.setExpiresAt(dto.getExpiresAt());
        document.setReviewNotes(trimToNull(dto.getNotes()));
        document.setReviewedByUserId(reviewerUserId);
        document.setReviewedAt(ZonedDateTime.now());
        documentRepository.save(document);

        // La fila guarda la última revisión; cada decisión queda en la auditoría CRITICAL.
        Map<String, Object> data = new HashMap<>();
        data.put("documentId", document.getId());
        data.put("documentType", document.getDocumentType().name());
        data.put("result", result.name());
        data.put("reviewerUserId", reviewerUserId);
        data.put("issuedAt", dto.getIssuedAt() != null ? dto.getIssuedAt().toString() : null);
        data.put("expiresAt", dto.getExpiresAt() != null ? dto.getExpiresAt().toString() : null);
        data.put("notes", document.getReviewNotes());
        publishAudit(commercial.getId(), "COMMERCIAL_DOCUMENT_REVIEWED",
                "Compliance revisó " + document.getDocumentType() + ": " + result, data);

        return toDTO(document);
    }

    // ==================== HELPERS ====================

    private void requireReplaceable(CommercialDocument replaced, ExpedienteDocumentUploadRequestDTO dto) {
        if (replaced.getStatus() != CommercialDocumentStatus.VALIDATED) {
            throw new InvalidRequestException("Solo se puede reemplazar un documento vigente.");
        }
        if (replaced.getDocumentType() != dto.getDocumentType()) {
            throw new InvalidRequestException("El reemplazo debe ser del mismo tipo de documento ("
                    + replaced.getDocumentType() + ").");
        }
    }

    private void requireAllowedMime(String contentType) {
        SupportedMimeType declared;
        try {
            declared = SupportedMimeType.fromValue(contentType);
        } catch (ValidationException e) {
            throw new ValidationException("Tipo de archivo no soportado. Use PDF, JPEG o PNG.");
        }
        if (!ALLOWED_MIME_TYPES.contains(declared)) {
            throw new ValidationException("Tipo de archivo no soportado. Use PDF, JPEG o PNG.");
        }
    }

    private CommercialDocument getOwnedOrThrow(Long documentId, Long commercialId) {
        return documentRepository.findByIdAndCommercial_Id(documentId, commercialId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Documento no encontrado: " + documentId, CommercialDocument.class));
    }

    private ExpedienteDocumentDTO toDTO(CommercialDocument d) {
        boolean expired = d.getExpiresAt() != null && d.getExpiresAt().isBefore(LocalDate.now(BOGOTA));
        String downloadUrl = d.getStatus() == CommercialDocumentStatus.PENDING
                ? null : r2Service.getPrivateObject(d.getObjectKey(), DOWNLOAD_URL_SECONDS);
        return new ExpedienteDocumentDTO(
                d.getId(), d.getDocumentType(), d.getOriginalFileName(), d.getSizeBytes(), d.getStatus(),
                d.getUploadedAt(),
                d.getReplacesDocument() != null ? d.getReplacesDocument().getId() : null,
                d.getSupersededAt(), d.getReviewStatus(), d.getReviewedAt(), d.getReviewNotes(),
                d.getHolderIdentification(), d.getIssuedAt(), d.getExpiresAt(), expired, downloadUrl);
    }

    private void publishAudit(Long commercialId, String action, String description, Map<String, Object> data) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(commercialId)
                    .action(action)
                    // Evidencia del expediente: CRITICAL no se borra en AuditCleanupJob.
                    .level(AuditLevel.CRITICAL)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(CommercialExpedienteDocumentServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(data)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
