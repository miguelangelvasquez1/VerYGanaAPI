package com.verygana2.services.tax;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.ComplianceTaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileSummary;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileVersionDetail;
import com.verygana2.dtos.tax.TaxProfileDTOs.VersionSummary;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO.ActivityDTO;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO.IncomeReportDTO;
import com.verygana2.dtos.tax.TaxRequestDTOs.SectionReviewRequest;
import com.verygana2.event.TaxProfileChangedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.tax.IncomeScope;
import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxCatalogType;
import com.verygana2.models.enums.tax.TaxProfileChangeType;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.models.tax.TaxIncomeReport;
import com.verygana2.models.tax.TaxProfile;
import com.verygana2.models.tax.TaxProfileActivity;
import com.verygana2.models.tax.TaxProfileSectionReview;
import com.verygana2.models.tax.TaxProfileVersion;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.tax.TaxProfileRepository;
import com.verygana2.repositories.tax.TaxProfileSectionReviewRepository;
import com.verygana2.repositories.tax.TaxProfileVersionRepository;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.declarations.DeclarationService;
import com.verygana2.services.interfaces.tax.CommercialEstablishmentService;
import com.verygana2.services.interfaces.tax.TaxCatalogService;
import com.verygana2.services.interfaces.tax.TaxProfileService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaxProfileServiceImpl implements TaxProfileService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final TaxProfileRepository profileRepository;
    private final TaxProfileVersionRepository versionRepository;
    private final TaxProfileSectionReviewRepository reviewRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final CommercialOnboardingRepository onboardingRepository;
    private final CommercialDocumentRepository documentRepository;
    private final TaxCatalogService catalogService;
    private final CommercialEstablishmentService establishmentService;
    private final UserIdResolver userIdResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final DeclarationService declarationService;

    // ==================== EMPRESARIO ====================

    @Override
    @Transactional(readOnly = true)
    public TaxProfileResponse getOwn(Long commercialId) {
        return profileRepository.findByCommercial_Id(commercialId).map(this::toResponse).orElse(null);
    }

    @Override
    public TaxProfileResponse submit(Long commercialId, TaxProfileSubmitRequestDTO request,
            String ipAddress, String userAgent) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));
        CommercialOnboarding onboarding = onboardingRepository.findByCommercialDetails_Id(commercialId)
                .orElseThrow(() -> new InvalidRequestException("No existe un proceso de registro para este empresario."));
        if (onboarding.getDocumentsCompletedAt() == null) {
            throw new InvalidRequestException("Complete la carga documental antes de diligenciar la ficha tributaria.");
        }

        validateActivities(request.getActivities());
        validateCodes(request);
        validateIncomeReports(request.getIncomeReports());
        CommercialDocument rutDocument = request.getRutDocumentId() == null ? null
                : requireOwnedValidatedDocument(request.getRutDocumentId(), commercialId, CommercialDocumentType.RUT);

        TaxProfile profile = profileRepository.findByCommercial_Id(commercialId).orElseGet(() -> {
            TaxProfile created = new TaxProfile();
            created.setCommercial(commercial);
            created.setCurrentVersion(0);
            return created;
        });
        Set<String> previousCiius = previousCiiuCodes(profile);

        int versionNumber = profile.getCurrentVersion() + 1;
        profile.setCurrentVersion(versionNumber);
        profile.setStatus(TaxProfileStatus.SUBMITTED);
        profile = profileRepository.save(profile);

        TaxProfileVersion version = buildVersion(profile, versionNumber, request, rutDocument, commercialId);
        version.setSubmittedByUserId(commercialId);
        version.setSubmittedAt(ZonedDateTime.now());
        version.setSubmittedIp(ipAddress);
        version.setSubmittedUserAgent(truncate(userAgent, 500));
        versionRepository.save(version);

        // Paso del registro: la ficha va después de los documentos y antes del contrato.
        if (onboarding.getCurrentStep() == OnboardingStep.TAX_PROFILE_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.CONTRACT_PENDING);
            onboardingRepository.save(onboarding);
        }

        Set<String> newCiius = request.getActivities().stream().map(ActivityDTO::getCiiuCode).collect(Collectors.toSet());
        if (!previousCiius.isEmpty() && !previousCiius.equals(newCiius)) {
            eventPublisher.publishEvent(new TaxProfileChangedEvent(this, commercialId,
                    TaxProfileChangeType.ACTIVITY_CHANGE,
                    "Actividades CIIU " + previousCiius + " → " + newCiius + " (ficha v" + versionNumber + ")"));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("versionNumber", versionNumber);
        data.put("taxProfileId", profile.getId());
        publishAudit(commercialId, "TAX_PROFILE_SUBMITTED",
                "Empresario envió la ficha tributaria v" + versionNumber + ".", ipAddress, userAgent, data);

        return toResponse(profile, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionSummary> listVersions(Long commercialId) {
        return profileRepository.findByCommercial_Id(commercialId)
                .map(p -> versionRepository.findByTaxProfile_IdOrderByVersionNumberDesc(p.getId()).stream()
                        .map(v -> new VersionSummary(v.getVersionNumber(), v.getSubmittedAt())).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public TaxProfileVersionDetail getVersion(Long commercialId, int versionNumber) {
        TaxProfile profile = profileRepository.findByCommercial_Id(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Todavía no ha enviado la ficha tributaria."));
        return TaxProfileViews.version(getVersionOrThrow(profile, versionNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasProfile(Long commercialId) {
        return profileRepository.existsByCommercial_Id(commercialId);
    }

    // ==================== COMPLIANCE ====================

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaxProfileSummary> list(TaxProfileStatus status, Pageable pageable) {
        Page<TaxProfile> page = status == null
                ? profileRepository.findAll(pageable)
                : profileRepository.findByStatus(status, pageable);
        return PagedResponse.from(page.map(p -> new TaxProfileSummary(
                userIdResolver.toPublicId(p.getCommercial().getId()), p.getCommercial().getCompanyName(),
                p.getStatus(), p.getCurrentVersion(), p.getUpdatedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceTaxProfileResponse getForCompliance(UUID commercialPublicId) {
        return toComplianceResponse(getProfileByPublicIdOrThrow(commercialPublicId));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxProfileVersionDetail getVersionForCompliance(UUID commercialPublicId, int versionNumber) {
        return TaxProfileViews.version(getVersionOrThrow(getProfileByPublicIdOrThrow(commercialPublicId), versionNumber));
    }

    @Override
    public ComplianceTaxProfileResponse reviewSection(UUID commercialPublicId, TaxProfileSection section,
            Long reviewerUserId, SectionReviewRequest request) {
        TaxProfile profile = getProfileByPublicIdOrThrow(commercialPublicId);
        if (request.getResult() == SectionReviewResult.OBSERVED
                && (request.getNotes() == null || request.getNotes().isBlank())) {
            throw new InvalidRequestException("Indique la observación para que el empresario pueda aclararla.");
        }
        // Solo se revisa la versión vigente: las anteriores son antecedente.
        TaxProfileVersion current = getVersionOrThrow(profile, profile.getCurrentVersion());

        TaxProfileSectionReview review = new TaxProfileSectionReview();
        review.setVersion(current);
        review.setSection(section);
        review.setResult(request.getResult());
        review.setNotes(trimToNull(request.getNotes()));
        review.setReviewedByUserId(reviewerUserId);
        review.setReviewedAt(ZonedDateTime.now());
        reviewRepository.save(review);

        profile.setStatus(TaxProfileViews.statusFrom(TaxProfileViews.latestBySection(
                reviewRepository.findByVersion_IdOrderByReviewedAtDescIdDesc(current.getId()))));
        profileRepository.save(profile);

        Map<String, Object> data = new HashMap<>();
        data.put("versionNumber", current.getVersionNumber());
        data.put("section", section.name());
        data.put("result", request.getResult().name());
        data.put("reviewerUserId", reviewerUserId);
        publishAudit(profile.getCommercial().getId(), "TAX_PROFILE_SECTION_REVIEWED",
                "Compliance revisó la sección " + section + " de la ficha v" + current.getVersionNumber()
                        + ": " + request.getResult(), null, null, data);

        return toComplianceResponse(profile);
    }

    // ==================== VALIDACIÓN ====================

    private void validateActivities(List<ActivityDTO> activities) {
        long mains = activities.stream().filter(a -> Boolean.TRUE.equals(a.getMain())).count();
        if (mains != 1) {
            throw new InvalidRequestException("Marque exactamente una actividad económica como principal.");
        }
        long distinct = activities.stream().map(ActivityDTO::getCiiuCode).distinct().count();
        if (distinct != activities.size()) {
            throw new InvalidRequestException("Hay códigos CIIU repetidos.");
        }
    }

    private void validateCodes(TaxProfileSubmitRequestDTO request) {
        catalogService.requireActiveCode(TaxCatalogType.REGIME, trimToNull(request.getRegimeCode()));
        catalogService.requireActiveCode(TaxCatalogType.VAT_CONDITION, trimToNull(request.getVatConditionCode()));
        catalogService.requireActiveCode(TaxCatalogType.INC_CONDITION, trimToNull(request.getIncConditionCode()));
        catalogService.requireActiveCode(TaxCatalogType.INVOICING_METHOD, trimToNull(request.getInvoicingMethodCode()));
        Set<String> seen = new HashSet<>();
        for (String code : request.getRutResponsibilityCodes()) {
            String trimmed = code.trim();
            if (!seen.add(trimmed)) {
                throw new InvalidRequestException("La responsabilidad RUT " + trimmed + " está repetida.");
            }
            catalogService.requireActiveCode(TaxCatalogType.RUT_RESPONSIBILITY, trimmed);
        }
    }

    /**
     * Periodos coherentes, sin valores futuros (una proyección no es un hecho) y sin
     * registros superpuestos: un total consolidado ya incluye las ventas de la plataforma,
     * así que no puede convivir con otro valor del mismo concepto y periodo.
     */
    private void validateIncomeReports(List<IncomeReportDTO> reports) {
        LocalDate today = LocalDate.now(BOGOTA);
        for (IncomeReportDTO r : reports) {
            if (r.getPeriodEnd().isBefore(r.getPeriodStart())) {
                throw new InvalidRequestException("El fin del periodo no puede ser anterior al inicio.");
            }
            if (r.getPeriodEnd().isAfter(today)) {
                throw new InvalidRequestException(
                        "Informe solo periodos ya cerrados: una estimación futura no se registra como ingreso.");
            }
        }
        for (int i = 0; i < reports.size(); i++) {
            for (int j = i + 1; j < reports.size(); j++) {
                IncomeReportDTO a = reports.get(i);
                IncomeReportDTO b = reports.get(j);
                if (a.getConcept() == b.getConcept() && overlaps(a, b) && scopesCollide(a.getScope(), b.getScope())) {
                    throw new InvalidRequestException("Hay valores de " + a.getConcept()
                            + " superpuestos para el mismo periodo. Si un total ya incluye las ventas de VERYGANA, "
                            + "no las informe otra vez por separado.");
                }
            }
        }
    }

    private static boolean overlaps(IncomeReportDTO a, IncomeReportDTO b) {
        return !a.getPeriodEnd().isBefore(b.getPeriodStart()) && !b.getPeriodEnd().isBefore(a.getPeriodStart());
    }

    private static boolean scopesCollide(IncomeScope a, IncomeScope b) {
        return a == b || a == IncomeScope.CONSOLIDATED_INCLUDES_PLATFORM || b == IncomeScope.CONSOLIDATED_INCLUDES_PLATFORM;
    }

    private CommercialDocument requireOwnedValidatedDocument(Long documentId, Long commercialId,
            CommercialDocumentType expectedType) {
        CommercialDocument document = documentRepository.findByIdAndCommercial_Id(documentId, commercialId)
                .orElseThrow(() -> new InvalidRequestException("El documento " + documentId + " no está en su expediente."));
        if (document.getStatus() != CommercialDocumentStatus.VALIDATED) {
            throw new InvalidRequestException("El documento " + documentId + " no está vigente en su expediente.");
        }
        if (expectedType != null && document.getDocumentType() != expectedType) {
            throw new InvalidRequestException("El documento " + documentId + " no es de tipo " + expectedType + ".");
        }
        return document;
    }

    // ==================== CONSTRUCCIÓN ====================

    private TaxProfileVersion buildVersion(TaxProfile profile, int versionNumber, TaxProfileSubmitRequestDTO request,
            CommercialDocument rutDocument, Long commercialId) {
        TaxProfileVersion version = new TaxProfileVersion();
        version.setTaxProfile(profile);
        version.setVersionNumber(versionNumber);

        version.setRutDocument(rutDocument);
        version.getRutResponsibilityCodes().addAll(request.getRutResponsibilityCodes().stream().map(String::trim).toList());

        version.setRegimeCode(trimToNull(request.getRegimeCode()));
        for (ActivityDTO a : request.getActivities()) {
            TaxProfileActivity activity = new TaxProfileActivity();
            activity.setVersion(version);
            activity.setCiiuCode(a.getCiiuCode());
            activity.setDescription(trimToNull(a.getDescription()));
            activity.setMain(Boolean.TRUE.equals(a.getMain()));
            version.getActivities().add(activity);
        }

        version.setVatConditionCode(trimToNull(request.getVatConditionCode()));
        version.setIncConditionCode(trimToNull(request.getIncConditionCode()));
        version.setTaxConditionScope(trimToNull(request.getTaxConditionScope()));

        version.setInvoicingObligated(request.getInvoicingObligated());
        version.setInvoicingMethodCode(trimToNull(request.getInvoicingMethodCode()));
        version.setInvoicingNotes(trimToNull(request.getInvoicingNotes()));
        version.setWithholdingAgent(request.getWithholdingAgent());
        version.setWithholdingNotes(trimToNull(request.getWithholdingNotes()));

        for (IncomeReportDTO r : request.getIncomeReports()) {
            TaxIncomeReport report = new TaxIncomeReport();
            report.setVersion(version);
            report.setConcept(r.getConcept());
            report.setPeriodStart(r.getPeriodStart());
            report.setPeriodEnd(r.getPeriodEnd());
            report.setAmountCents(r.getAmountCents());
            report.setCurrency(r.getCurrency());
            report.setScope(r.getScope());
            report.setSupportDocument(r.getSupportDocumentId() == null ? null
                    : requireOwnedValidatedDocument(r.getSupportDocumentId(), commercialId, null));
            report.setNotes(trimToNull(r.getNotes()));
            version.getIncomeReports().add(report);
        }

        version.setEstablishmentsCount(establishmentService.countActive(commercialId));

        version.setCurrentCapacity(request.getCurrentCapacity().trim());
        version.setGrowthScenario(trimToNull(request.getGrowthScenario()));
        version.setGrowthMeasures(trimToNull(request.getGrowthMeasures()));

        boolean hasAdvisor = request.getHasAccountingAdvisor();
        version.setHasAccountingAdvisor(hasAdvisor);
        version.setAdvisorScope(hasAdvisor ? trimToNull(request.getAdvisorScope()) : null);
        return version;
    }

    private Set<String> previousCiiuCodes(TaxProfile profile) {
        if (profile.getId() == null || profile.getCurrentVersion() == 0) {
            return Set.of();
        }
        return versionRepository.findByTaxProfile_IdAndVersionNumber(profile.getId(), profile.getCurrentVersion())
                .map(v -> v.getActivities().stream().map(TaxProfileActivity::getCiiuCode).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    // ==================== VISTAS ====================

    private TaxProfileResponse toResponse(TaxProfile profile) {
        return toResponse(profile, getVersionOrThrow(profile, profile.getCurrentVersion()));
    }

    private TaxProfileResponse toResponse(TaxProfile profile, TaxProfileVersion current) {
        var latest = current.getId() == null ? Map.<TaxProfileSection, TaxProfileSectionReview>of()
                : TaxProfileViews.latestBySection(reviewRepository.findByVersion_IdOrderByReviewedAtDescIdDesc(current.getId()));
        Long commercialId = profile.getCommercial().getId();
        return new TaxProfileResponse(profile.getStatus(), profile.getCurrentVersion(), profile.getUpdatedAt(),
                TaxProfileViews.version(current), TaxProfileViews.sectionReviews(latest),
                establishmentService.listActive(commercialId),
                declarationService.currentManifestation(commercialId, DeclarationCode.FORMALIZATION_COMMITMENT));
    }

    private ComplianceTaxProfileResponse toComplianceResponse(TaxProfile profile) {
        CommercialDetails commercial = profile.getCommercial();
        List<VersionSummary> versions = versionRepository.findByTaxProfile_IdOrderByVersionNumberDesc(profile.getId())
                .stream().map(v -> new VersionSummary(v.getVersionNumber(), v.getSubmittedAt())).toList();
        return new ComplianceTaxProfileResponse(
                userIdResolver.toPublicId(commercial.getId()), commercial.getCompanyName(), commercial.getNit(),
                commercial.getCommercialActivityType(), toResponse(profile), versions,
                establishmentService.list(commercial.getId()));
    }

    private TaxProfile getProfileByPublicIdOrThrow(UUID commercialPublicId) {
        Long commercialId = userIdResolver.toInternalId(commercialPublicId);
        return profileRepository.findByCommercial_Id(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("El empresario todavía no ha enviado la ficha tributaria."));
    }

    private TaxProfileVersion getVersionOrThrow(TaxProfile profile, int versionNumber) {
        return versionRepository.findByTaxProfile_IdAndVersionNumber(profile.getId(), versionNumber)
                .orElseThrow(() -> new EntityNotFoundException("Versión " + versionNumber + " de la ficha no encontrada."));
    }

    private void publishAudit(Long commercialId, String action, String description, String ip, String userAgent,
            Map<String, Object> data) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(commercialId)
                    .action(action)
                    // Evidencia de la caracterización: CRITICAL no se borra en AuditCleanupJob.
                    .level(AuditLevel.CRITICAL)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(TaxProfileServiceImpl.class.getName())
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(data)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
