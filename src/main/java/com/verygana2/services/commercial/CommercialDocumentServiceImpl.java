package com.verygana2.services.commercial;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentsStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentChecklistItemDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadPermissionDTO;
import com.verygana2.dtos.user.commercial.onboarding.DocumentUploadRequestDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.services.interfaces.commercial.CommercialDocumentService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDocumentServiceImpl implements CommercialDocumentService {

    private static final Set<SupportedMimeType> ALLOWED_MIME_TYPES = Set.of(
            SupportedMimeType.APPLICATION_PDF, SupportedMimeType.IMAGE_JPEG, SupportedMimeType.IMAGE_PNG);
    private static final String OBJECT_KEY_PREFIX = "commercial-documents/";
    private static final String PRIVATE_PREFIX = "private/";

    private final CommercialOnboardingRepository onboardingRepository;
    private final CommercialDocumentRepository documentRepository;
    private final R2Service r2Service;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${commercial.onboarding.documents.max-size-bytes}")
    private long maxSizeBytes;

    @Override
    public DocumentUploadPermissionDTO prepareUpload(Long userId, DocumentUploadRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireEditable(onboarding);

        if (dto.getSizeBytes() > maxSizeBytes) {
            throw new ValidationException(
                    "Archivo muy grande. Máximo permitido: " + (maxSizeBytes / 1024 / 1024) + " MB");
        }

        SupportedMimeType declaredMime;
        try {
            declaredMime = SupportedMimeType.fromValue(dto.getContentType());
        } catch (ValidationException e) {
            throw new ValidationException("Tipo de archivo no soportado. Use PDF, JPEG o PNG.");
        }
        if (!ALLOWED_MIME_TYPES.contains(declaredMime)) {
            throw new ValidationException("Tipo de archivo no soportado. Use PDF, JPEG o PNG.");
        }

        String objectKey = OBJECT_KEY_PREFIX + onboarding.getId() + "/" + UUID.randomUUID();

        CommercialDocument document = new CommercialDocument();
        document.setOnboarding(onboarding);
        document.setDocumentType(dto.getDocumentType());
        document.setObjectKey(objectKey);
        document.setOriginalFileName(dto.getOriginalFileName());
        document.setSizeBytes(dto.getSizeBytes());
        document.setStatus(CommercialDocumentStatus.PENDING);
        CommercialDocument saved = documentRepository.save(document);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, dto.getContentType());

        return new DocumentUploadPermissionDTO(saved.getId(), permission);
    }

    // 6. CARGAR DOCUMENTOS. El avance de paso NO es automático — ver continueToContract().
    @Override
    public CommercialDocumentsStatusResponseDTO confirmUpload(Long userId, Long documentId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireEditable(onboarding);
        CommercialDocument document = getDocumentOrThrow(documentId, onboarding.getId());

        SupportedMimeType realMime = r2Service.validateUploadedObject(
                true, document.getObjectKey(), document.getSizeBytes(), maxSizeBytes, ALLOWED_MIME_TYPES);

        document.setMimeType(realMime);
        document.setStatus(CommercialDocumentStatus.VALIDATED);
        documentRepository.save(document);

        publishAudit(userId, "COMMERCIAL_DOCUMENT_UPLOADED",
                "Comercial cargó documento " + document.getDocumentType(), document.getDocumentType().name());

        return buildStatus(onboarding);
    }

    @Override
    public CommercialDocumentsStatusResponseDTO discard(Long userId, Long documentId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireEditable(onboarding);
        CommercialDocument document = getDocumentOrThrow(documentId, onboarding.getId());

        try {
            r2Service.deleteObject(PRIVATE_PREFIX + document.getObjectKey());
        } catch (Exception e) {
            log.warn("No se pudo eliminar el objeto {} de storage: {}", document.getObjectKey(), e.getMessage());
        }
        document.setStatus(CommercialDocumentStatus.ORPHANED);
        documentRepository.save(document);

        if (onboarding.getDocumentsCompletedAt() != null && !isAllRequiredUploaded(onboarding)) {
            onboarding.setDocumentsCompletedAt(null);
            if (onboarding.getCurrentStep() == OnboardingStep.CONTRACT_PENDING) {
                onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
            }
            onboardingRepository.save(onboarding);
        }

        return buildStatus(onboarding);
    }

    @Override
    @Transactional(readOnly = true)
    public CommercialDocumentsStatusResponseDTO getStatus(Long userId) {
        return buildStatus(getOnboardingOrThrow(userId));
    }

    // 6b. EL COMERCIAL CONFIRMA LA CARGA DOCUMENTAL COMPLETA Y AVANZA A CONTRACT_PENDING
    @Override
    public CommercialDocumentsStatusResponseDTO continueToContract(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireEditable(onboarding);

        if (!isAllRequiredUploaded(onboarding)) {
            throw new OnboardingStepException("Debe cargar todos los documentos requeridos antes de continuar.");
        }

        onboarding.setDocumentsCompletedAt(ZonedDateTime.now());
        if (onboarding.getCurrentStep() == OnboardingStep.DOCUMENTS_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.CONTRACT_PENDING);
        }
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_DOCUMENTS_COMPLETED",
                "Comercial confirmó la carga documental completa.", "ALL_REQUIRED_DOCUMENTS");

        return buildStatus(onboarding);
    }

    // ==================== HELPERS ====================

    private boolean isAllRequiredUploaded(CommercialOnboarding onboarding) {
        List<CommercialDocument> validated = documentRepository.findByOnboarding_IdAndStatus(
                onboarding.getId(), CommercialDocumentStatus.VALIDATED);
        for (CommercialDocumentType type : CommercialDocumentType.values()) {
            if (isRequired(type, onboarding) && validated.stream().noneMatch(d -> d.getDocumentType() == type)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRequired(CommercialDocumentType type, CommercialOnboarding onboarding) {
        return switch (type) {
            case RUT, CEDULA_REPRESENTANTE, CERTIFICACION_BANCARIA -> true;
            case CAMARA_COMERCIO -> onboarding.getPersonType() == PersonType.JURIDICA;
            case PERMISO_SECTORIAL -> Boolean.TRUE.equals(onboarding.getRegulatedSector());
            case MARCA_REGISTRADA, OTRO -> false;
        };
    }

    private CommercialDocumentsStatusResponseDTO buildStatus(CommercialOnboarding onboarding) {
        List<CommercialDocument> current = documentRepository.findByOnboarding_IdAndStatusNot(
                onboarding.getId(), CommercialDocumentStatus.ORPHANED);

        List<CommercialDocumentResponseDTO> documents = current.stream()
                .map(d -> new CommercialDocumentResponseDTO(
                        d.getId(), d.getDocumentType(), d.getOriginalFileName(), d.getSizeBytes(), d.getStatus(),
                        d.getUploadedAt(),
                        d.getStatus() == CommercialDocumentStatus.VALIDATED
                                ? r2Service.getPrivateObject(d.getObjectKey(), 300) : null))
                .toList();

        List<DocumentChecklistItemDTO> checklist = List.of(CommercialDocumentType.values()).stream()
                .map(type -> new DocumentChecklistItemDTO(
                        type, isRequired(type, onboarding),
                        current.stream().anyMatch(d -> d.getDocumentType() == type
                                && d.getStatus() == CommercialDocumentStatus.VALIDATED)))
                .toList();

        return new CommercialDocumentsStatusResponseDTO(documents, checklist, isAllRequiredUploaded(onboarding));
    }

    private void requireEditable(CommercialOnboarding onboarding) {
        if (onboarding.getPlanAcceptedAt() == null) {
            throw new OnboardingStepException("Debe aceptar el plan antes de cargar documentos.");
        }
        OnboardingStep step = onboarding.getCurrentStep();
        if (step == OnboardingStep.BUSINESS_REVIEW_PENDING
                || step == OnboardingStep.VERYGANA_REVIEW_PENDING
                || step == OnboardingStep.SIGNATURE_PENDING
                || step == OnboardingStep.PAYMENT_PENDING
                || step == OnboardingStep.COMPLETED) {
            throw new OnboardingStepException(
                    "No puede modificar documentos en este punto del proceso. "
                            + "Solicite cambios desde la revisión del contrato (POST /commercials/onboarding/contract/request-changes).");
        }
    }

    private CommercialOnboarding getOnboardingOrThrow(Long userId) {
        return onboardingRepository.findByCommercialDetails_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No existe un proceso de onboarding comercial para userId: " + userId, CommercialOnboarding.class));
    }

    private CommercialDocument getDocumentOrThrow(Long documentId, Long onboardingId) {
        return documentRepository.findByIdAndOnboarding_Id(documentId, onboardingId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Documento no encontrado: " + documentId, CommercialDocument.class));
    }

    private void publishAudit(Long userId, String action, String description, String documentType) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(userId)
                    .action(action)
                    .level(AuditLevel.INFO)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(CommercialDocumentServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(java.util.Map.of("documentType", documentType))
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }
}
