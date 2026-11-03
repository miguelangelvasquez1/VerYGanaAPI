package com.verygana2.services.declarations;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.declarations.DeclarationDTOs.DeclarationView;
import com.verygana2.dtos.declarations.DeclarationDTOs.Manifestation;
import com.verygana2.dtos.user.commercial.onboarding.ContractApproveRequestDTO;
import com.verygana2.event.FormalizationManifestationRecordedEvent;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.declarations.DeclarationDisclosure;
import com.verygana2.models.declarations.DeclarationManifestation;
import com.verygana2.models.declarations.DeclarationText;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.DisclosureContext;
import com.verygana2.models.enums.declarations.ManifestationDecision;
import com.verygana2.models.enums.declarations.ManifestationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.declarations.DeclarationDisclosureRepository;
import com.verygana2.repositories.declarations.DeclarationManifestationRepository;
import com.verygana2.repositories.declarations.DeclarationTextRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.declarations.DeclarationService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeclarationServiceImpl implements DeclarationService {

    private final DeclarationTextRepository textRepository;
    private final DeclarationDisclosureRepository disclosureRepository;
    private final DeclarationManifestationRepository manifestationRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final UserIdResolver userIdResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public DeclarationText activeText(DeclarationCode code) {
        return textRepository.findFirstByCodeAndActiveTrueOrderByPublishedAtDesc(code)
                .orElseThrow(() -> new IllegalStateException("No hay un texto activo para la declaración " + code));
    }

    @Override
    public DeclarationView viewForCommercial(Long commercialId, DeclarationCode code, DisclosureContext context) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));
        DeclarationText text = activeText(code);

        DeclarationDisclosure disclosure = new DeclarationDisclosure();
        disclosure.setCommercial(commercial);
        disclosure.setDeclarationText(text);
        disclosure.setContext(context);
        disclosure.setDisclosedAt(ZonedDateTime.now());
        disclosureRepository.save(disclosure);

        return new DeclarationView(code, text.getVersion(), text.getFullText(), text.getTextHash(),
                currentManifestation(commercialId, code));
    }

    @Override
    @Transactional(readOnly = true)
    public Manifestation currentManifestation(Long commercialId, DeclarationCode code) {
        List<DeclarationManifestation> history =
                manifestationRepository.findByCommercial_IdAndCodeOrderByOccurredAtDescIdDesc(commercialId, code);
        return history.stream()
                .filter(m -> m.getStatus() == ManifestationStatus.MANIFESTED)
                .findFirst()
                .or(() -> history.stream().findFirst())
                .map(DeclarationServiceImpl::toDTO)
                .orElse(null);
    }

    @Override
    public Manifestation recordContractManifestation(CommercialOnboarding onboarding, CommercialContract contract,
            ContractApproveRequestDTO request, String idempotencyKey, Long actorUserId, String ipAddress,
            String userAgent) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null) {
            Optional<DeclarationManifestation> retried = manifestationRepository.findByIdempotencyKey(key);
            if (retried.isPresent()) {
                return toDTO(retried.get());
            }
        }

        DeclarationText text = activeText(DeclarationCode.FORMALIZATION_COMMITMENT);
        // Ya manifestada en este proceso contractual: se muestra como evidencia, no se pide otra.
        Optional<DeclarationManifestation> existing = manifestationRepository
                .findFirstByContract_IdAndCodeAndDeclarationText_IdAndStatus(contract.getId(),
                        DeclarationCode.FORMALIZATION_COMMITMENT, text.getId(), ManifestationStatus.MANIFESTED);
        if (existing.isPresent()) {
            return toDTO(existing.get());
        }

        ContractApproveRequestDTO input = request != null ? request : new ContractApproveRequestDTO();
        Outcome outcome = classify(input, text);

        CommercialDetails commercial = contract.getCommercial();
        DeclarationManifestation manifestation = new DeclarationManifestation();
        manifestation.setCommercial(commercial);
        manifestation.setCode(DeclarationCode.FORMALIZATION_COMMITMENT);
        manifestation.setDeclarationText(text);
        manifestation.setContract(contract);
        manifestation.setContractVersion(contract.getVersion());
        manifestation.setStatus(outcome.status());
        manifestation.setDetail(outcome.detail());
        manifestation.setActorUserId(actorUserId);
        manifestation.setActorLegalRepName(legalRepName(onboarding));
        manifestation.setActorLegalRepDocType(commercial.getLegalRepDocType());
        manifestation.setActorLegalRepDocNumber(commercial.getLegalRepDocNumber());
        manifestation.setOccurredAt(ZonedDateTime.now());
        manifestation.setIpAddress(ipAddress);
        manifestation.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        manifestation.setIdempotencyKey(key);
        DeclarationManifestation saved = manifestationRepository.save(manifestation);

        eventPublisher.publishEvent(new FormalizationManifestationRecordedEvent(this, commercial.getId(),
                saved.getId(), saved.getStatus()));

        Map<String, Object> data = new HashMap<>();
        data.put("manifestationId", saved.getId());
        data.put("status", saved.getStatus().name());
        data.put("declarationVersion", text.getVersion());
        data.put("contractId", contract.getId());
        data.put("contractVersion", contract.getVersion());
        publishAudit(commercial.getId(), saved.getStatus(), text.getVersion(), ipAddress, userAgent, data);

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Manifestation> historyForCompliance(UUID commercialPublicId, DeclarationCode code) {
        Long commercialId = userIdResolver.toInternalId(commercialPublicId);
        return manifestationRepository.findByCommercial_IdAndCodeOrderByOccurredAtDescIdDesc(commercialId, code)
                .stream().map(DeclarationServiceImpl::toDTO).toList();
    }

    // ==================== HELPERS ====================

    private record Outcome(ManifestationStatus status, String detail) {
    }

    /**
     * Solo una acción expresa sobre el texto vigente cuenta como manifestación. Si la acción
     * viene sobre otra versión del texto (pantalla desactualizada), no se puede atribuir: es una
     * falla técnica, no una aceptación ni una negativa.
     */
    private static Outcome classify(ContractApproveRequestDTO input, DeclarationText text) {
        ManifestationDecision decision = input.getDecision();
        if (decision == null) {
            return new Outcome(ManifestationStatus.NOT_MANIFESTED, null);
        }
        if (input.getDeclarationVersion() == null || !text.getVersion().equals(input.getDeclarationVersion().trim())) {
            return new Outcome(ManifestationStatus.TECHNICAL_INCIDENT,
                    "La acción se envió sobre la versión '" + input.getDeclarationVersion()
                            + "' del texto y la vigente es '" + text.getVersion() + "'.");
        }
        if (decision == ManifestationDecision.REFUSE) {
            String reason = input.getRefusalReason();
            return new Outcome(ManifestationStatus.EXPRESS_REFUSAL,
                    reason == null || reason.isBlank() ? null : reason.trim());
        }
        return new Outcome(ManifestationStatus.MANIFESTED, null);
    }

    private static String legalRepName(CommercialOnboarding onboarding) {
        if (onboarding == null) {
            return null;
        }
        String first = onboarding.getLegalRepFirstName() != null ? onboarding.getLegalRepFirstName().trim() : "";
        String last = onboarding.getLegalRepLastName() != null ? onboarding.getLegalRepLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    private static Manifestation toDTO(DeclarationManifestation m) {
        return new Manifestation(m.getId(), m.getStatus(), m.getDeclarationText().getVersion(),
                m.getContract().getId(), m.getContractVersion(), m.getOccurredAt(), m.getDetail(),
                m.getActorLegalRepName());
    }

    private void publishAudit(Long commercialId, ManifestationStatus status, String version, String ip,
            String userAgent, Map<String, Object> data) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(commercialId)
                    .action("FORMALIZATION_COMMITMENT_" + status.name())
                    .level(AuditLevel.CRITICAL)
                    .category("COMPLIANCE")
                    .description("Compromiso de formalización v" + version + " en la Hoja Final: " + status)
                    .className(DeclarationServiceImpl.class.getName())
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(data)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría de la declaración", e);
        }
    }
}
