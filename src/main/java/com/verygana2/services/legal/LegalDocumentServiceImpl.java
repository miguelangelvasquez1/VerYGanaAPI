package com.verygana2.services.legal;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.legal.LegalDocumentPrepareUploadRequestDTO;
import com.verygana2.dtos.legal.LegalDocumentResponseDTO;
import com.verygana2.dtos.legal.LegalDocumentUploadPermissionDTO;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.services.interfaces.legal.LegalDocumentService;
import com.verygana2.storage.service.R2Service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LegalDocumentServiceImpl implements LegalDocumentService {

    private static final int MAX_DOCUMENTS_PER_TYPE = 10;
    private static final Set<SupportedMimeType> ALLOWED_MIME_TYPES = Set.of(SupportedMimeType.APPLICATION_PDF);

    private final LegalDocumentRepository legalDocumentRepository;
    private final R2Service r2Service;

    @Value("${legal.documents.max-size-bytes:10485760}")
    private long maxSizeBytes;

    @Override
    @Transactional(readOnly = true)
    public LegalDocumentResponseDTO getActive(LegalDocumentType type) {
        return toDTO(legalDocumentRepository.findByTypeAndActiveTrue(type)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No hay una versión activa publicada para: " + type, LegalDocument.class)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentResponseDTO> listAllActive() {
        return java.util.Arrays.stream(LegalDocumentType.values())
                .map(legalDocumentRepository::findByTypeAndActiveTrue)
                .flatMap(java.util.Optional::stream)
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentResponseDTO> listHistory(LegalDocumentType type) {
        return legalDocumentRepository
                .findByTypeAndStatusOrderByPublishedDateDesc(type, LegalDocumentStatus.VALIDATED).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public LegalDocumentUploadPermissionDTO prepareUpload(LegalDocumentPrepareUploadRequestDTO dto) {
        if (dto.getSizeBytes() > maxSizeBytes) {
            throw new ValidationException(
                    "Archivo muy grande. Máximo permitido: " + (maxSizeBytes / 1024 / 1024) + " MB");
        }
        if (!"application/pdf".equalsIgnoreCase(dto.getContentType())) {
            throw new ValidationException("El documento debe ser un PDF");
        }

        String version = computeNextVersion(dto.getType());
        String objectKey = "legal/" + dto.getType().name().toLowerCase() + "/"
                + version + "-" + UUID.randomUUID() + ".pdf";

        LegalDocument document = new LegalDocument();
        document.setType(dto.getType());
        document.setVersion(version);
        document.setPublishedDate(LocalDate.now());
        document.setOriginalFileName(dto.getOriginalFileName());
        document.setSizeBytes(dto.getSizeBytes());
        document.setObjectKey(objectKey);
        document.setDocumentUrl(r2Service.buildPublicUrl(objectKey));
        document.setStatus(LegalDocumentStatus.PENDING);
        document.setActive(false);
        LegalDocument saved = legalDocumentRepository.save(document);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(false, objectKey, dto.getContentType());
        return new LegalDocumentUploadPermissionDTO(saved.getId(), permission);
    }

    @Override
    public LegalDocumentResponseDTO confirmUpload(Long documentId) {
        LegalDocument document = getDocumentOrThrow(documentId);
        if (document.getStatus() != LegalDocumentStatus.PENDING) {
            throw new ValidationException("Este documento ya fue confirmado o descartado.");
        }

        // Valida tamaño y MIME real en R2 (fuente de verdad) — no confía en lo declarado en prepare-upload.
        r2Service.validateUploadedObject(
                false, document.getObjectKey(), document.getSizeBytes(), maxSizeBytes, ALLOWED_MIME_TYPES);

        legalDocumentRepository.findByTypeAndActiveTrue(document.getType()).ifPresent(previous -> {
            previous.setActive(false);
            legalDocumentRepository.save(previous);
        });

        document.setStatus(LegalDocumentStatus.VALIDATED);
        document.setActive(true);
        LegalDocument saved = legalDocumentRepository.save(document);

        enforceRetentionLimit(document.getType());

        log.info("Nueva versión publicada para {}: v{} ({})", document.getType(), document.getVersion(), document.getDocumentUrl());
        return toDTO(saved);
    }

    @Override
    public void discardUpload(Long documentId) {
        LegalDocument document = getDocumentOrThrow(documentId);
        if (document.getStatus() != LegalDocumentStatus.PENDING) {
            throw new ValidationException("Solo se pueden descartar subidas pendientes.");
        }

        try {
            r2Service.deleteObject("public/" + document.getObjectKey());
        } catch (Exception e) {
            log.warn("No se pudo eliminar de R2 la subida descartada (key={}): {}", document.getObjectKey(), e.getMessage());
        }
        legalDocumentRepository.delete(document);
    }

    /** Conserva como máximo MAX_DOCUMENTS_PER_TYPE versiones publicadas por tipo; borra las más antiguas que sobren. */
    private void enforceRetentionLimit(LegalDocumentType type) {
        List<LegalDocument> validated = legalDocumentRepository
                .findByTypeAndStatusOrderByCreatedAtAsc(type, LegalDocumentStatus.VALIDATED);
        int excess = validated.size() - MAX_DOCUMENTS_PER_TYPE;
        if (excess <= 0) {
            return;
        }
        for (LegalDocument oldest : validated.subList(0, excess)) {
            try {
                r2Service.deleteObject("public/" + oldest.getObjectKey());
            } catch (Exception e) {
                log.warn("No se pudo eliminar de R2 el documento legal más antiguo (id={}, key={}): {}",
                        oldest.getId(), oldest.getObjectKey(), e.getMessage());
            }
            legalDocumentRepository.delete(oldest);
            log.info("Documento legal más antiguo eliminado por retención: type={}, id={}, version={}",
                    type, oldest.getId(), oldest.getVersion());
        }
    }

    /**
     * Calcula la siguiente versión para un tipo: última versión existente (cualquier
     * estado) + 1. Si no hay ninguna, empieza en "1". Tolera versiones legadas tipo
     * "1.0" tomando su parte entera.
     */
    private String computeNextVersion(LegalDocumentType type) {
        return legalDocumentRepository.findFirstByTypeOrderByCreatedAtDesc(type)
                .map(d -> String.valueOf(parseVersionNumber(d.getVersion()) + 1))
                .orElse("1");
    }

    private int parseVersionNumber(String version) {
        try {
            return (int) Math.floor(Double.parseDouble(version));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LegalDocument getDocumentOrThrow(Long documentId) {
        return legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ObjectNotFoundException("Documento legal no encontrado: " + documentId, LegalDocument.class));
    }

    private LegalDocumentResponseDTO toDTO(LegalDocument d) {
        return new LegalDocumentResponseDTO(
                d.getId(), d.getType(), d.getVersion(), d.getDocumentUrl(), d.getStatus(), d.getPublishedDate(), d.isActive());
    }
}
