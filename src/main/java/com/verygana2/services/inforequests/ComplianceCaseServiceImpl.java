package com.verygana2.services.inforequests;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentView;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.declarations.ManifestationStatus;
import com.verygana2.models.enums.inforequests.ContractualMeasure;
import com.verygana2.models.enums.inforequests.FindingClassification;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.inforequests.ComplianceFinding;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.models.inforequests.TechnicalIncident;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.declarations.DeclarationManifestationRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceFindingRepository;
import com.verygana2.repositories.inforequests.InformationRequestRepository;
import com.verygana2.repositories.inforequests.TechnicalIncidentRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.AccountStatusService;
import com.verygana2.services.interfaces.inforequests.ComplianceCaseService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComplianceCaseServiceImpl implements ComplianceCaseService {

    /** Un error subsanable o una inconsistencia aislada no son prueba de fraude: no restringen ni suspenden. */
    private static final Set<FindingClassification> LIGHT_CLASSIFICATIONS =
            EnumSet.of(FindingClassification.SUBSANABLE_ERROR, FindingClassification.ISOLATED_INCONSISTENCY);
    private static final Set<ContractualMeasure> LIGHT_MEASURES =
            EnumSet.of(ContractualMeasure.NONE, ContractualMeasure.WARNING);

    private final ComplianceFindingRepository findingRepository;
    private final TechnicalIncidentRepository incidentRepository;
    private final InformationRequestRepository requestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final DeclarationManifestationRepository manifestationRepository;
    private final AccountStatusService accountStatusService;
    private final UserIdResolver userIdResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public FindingView recordFinding(FindingRequest dto, Long deciderUserId) {
        Long commercialId = userIdResolver.toInternalId(dto.getCommercialPublicId());
        CommercialDetails commercial = getCommercialOrThrow(commercialId);

        if (LIGHT_CLASSIFICATIONS.contains(dto.getClassification()) && !LIGHT_MEASURES.contains(dto.getMeasure())) {
            throw new InvalidRequestException(
                    "Un error subsanable o una inconsistencia aislada no justifican restringir, suspender ni "
                            + "remitir a terminación. Si hay hechos más graves, clasifíquelos según su naturaleza.");
        }
        // Una nota interna no reemplaza la evidencia de una negativa atribuida al empresario.
        if (dto.getClassification() == FindingClassification.EXPRESS_REFUSAL
                && !manifestationRepository.existsByCommercial_IdAndStatus(commercialId, ManifestationStatus.EXPRESS_REFUSAL)) {
            throw new InvalidRequestException(
                    "No hay una negativa expresa registrada del empresario. Una ausencia de manifestación o una falla "
                            + "técnica no son una negativa.");
        }

        InformationRequest request = null;
        if (dto.getRequestPublicId() != null) {
            request = requestRepository.findByPublicIdAndCommercial_Id(dto.getRequestPublicId(), commercialId)
                    .orElseThrow(() -> new InvalidRequestException("El requerimiento no es de este empresario."));
        }

        AccountStatus target = switch (dto.getMeasure()) {
            case PREVENTIVE_RESTRICTION -> AccountStatus.PREVENTIVELY_RESTRICTED;
            case SUSPENSION -> AccountStatus.SUSPENDED;
            case NONE, WARNING, TERMINATION_REFERRAL -> null;
        };

        ComplianceFinding finding = new ComplianceFinding();
        finding.setCommercial(commercial);
        finding.setRequest(request);
        finding.setFactObserved(dto.getFactObserved().trim());
        finding.setEvidence(trimToNull(dto.getEvidence()));
        finding.setClassification(dto.getClassification());
        finding.setSeverity(dto.getSeverity());
        finding.setImpact(dto.getImpact().trim());
        finding.setRemediable(dto.getRemediable());
        finding.setContractualBasis(dto.getContractualBasis().trim());
        finding.setMeasure(dto.getMeasure());
        finding.setMeasureScope(trimToNull(dto.getMeasureScope()));
        finding.setAccountStatusApplied(target);
        finding.setDecidedByUserId(deciderUserId);
        ComplianceFinding saved = findingRepository.save(finding);

        // El estado cambia con historial (AccountStatusHistory); nada se borra.
        if (target != null) {
            accountStatusService.transition(commercialId, target,
                    "Hallazgo " + saved.getPublicId() + " (" + dto.getClassification() + "): "
                            + truncate(dto.getContractualBasis().trim(), 300),
                    "COMPLIANCE_OFFICER:" + deciderUserId);
        }
        if (request != null && request.getStatus().isOpen()) {
            request.close(InformationRequestStatus.CLOSED_WITH_FINDING, deciderUserId, "Hallazgo " + saved.getPublicId());
            requestRepository.save(request);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("findingPublicId", saved.getPublicId().toString());
        data.put("classification", dto.getClassification().name());
        data.put("severity", dto.getSeverity().name());
        data.put("measure", dto.getMeasure().name());
        data.put("remediable", dto.getRemediable());
        data.put("accountStatusApplied", target != null ? target.name() : null);
        data.put("deciderUserId", deciderUserId);
        publishAudit(commercialId, data);

        return InformationRequestViews.finding(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FindingView> listFindings(UUID commercialPublicId) {
        return findingRepository.findByCommercial_IdOrderByDecidedAtDesc(userIdResolver.toInternalId(commercialPublicId))
                .stream().map(InformationRequestViews::finding).toList();
    }

    @Override
    public IncidentView recordIncident(IncidentRequest dto, Long reporterUserId) {
        Long commercialId = userIdResolver.toInternalId(dto.getCommercialPublicId());
        InformationRequest request = null;
        if (dto.getRequestPublicId() != null) {
            request = requestRepository.findByPublicIdAndCommercial_Id(dto.getRequestPublicId(), commercialId)
                    .orElseThrow(() -> new InvalidRequestException("El requerimiento no es de este empresario."));
        }
        TechnicalIncident incident = newIncident(getCommercialOrThrow(commercialId), dto.getContext().trim(),
                dto.getDescription().trim(), null, request);
        incident.setReportedByUserId(reporterUserId);
        return InformationRequestViews.incident(incidentRepository.save(incident));
    }

    @Override
    public void recordSystemIncident(Long commercialId, String context, String description, Long manifestationId,
            InformationRequest request) {
        incidentRepository.save(newIncident(getCommercialOrThrow(commercialId), context, description,
                manifestationId, request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentView> listIncidents(UUID commercialPublicId) {
        return incidentRepository.findByCommercial_IdOrderByOccurredAtDesc(userIdResolver.toInternalId(commercialPublicId))
                .stream().map(InformationRequestViews::incident).toList();
    }

    // ==================== HELPERS ====================

    private static TechnicalIncident newIncident(CommercialDetails commercial, String context, String description,
            Long manifestationId, InformationRequest request) {
        TechnicalIncident incident = new TechnicalIncident();
        incident.setCommercial(commercial);
        incident.setContext(context);
        incident.setDescription(truncate(description, 1000));
        incident.setManifestationId(manifestationId);
        incident.setRequest(request);
        incident.setOccurredAt(ZonedDateTime.now());
        return incident;
    }

    private CommercialDetails getCommercialOrThrow(Long commercialId) {
        return commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));
    }

    private void publishAudit(Long commercialId, Map<String, Object> data) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(commercialId)
                    .action("COMPLIANCE_FINDING_RECORDED")
                    .level(AuditLevel.CRITICAL)
                    .category("COMPLIANCE")
                    .description("Compliance registró un hallazgo con medida " + data.get("measure") + ".")
                    .className(ComplianceCaseServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(data)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría del hallazgo", e);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
