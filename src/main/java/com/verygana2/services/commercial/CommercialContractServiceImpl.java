package com.verygana2.services.commercial;

import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractReviewListItemDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.commercial.ESignatureService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialContractServiceImpl implements CommercialContractService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale CO_LOCALE = Locale.of("es", "CO");
    private static final List<ContractStatus> COMPLIANCE_RELEVANT_STATUSES = List.of(
            ContractStatus.PENDING_VERYGANA_REVIEW, ContractStatus.APPROVED,
            ContractStatus.PENDING_SIGNATURE, ContractStatus.SIGNED, ContractStatus.REJECTED);

    private final CommercialOnboardingRepository onboardingRepository;
    private final CommercialContractRepository contractRepository;
    private final CommercialDocumentRepository documentRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final ContractTemplateLoader templateLoader;
    private final ContractPdfRenderer pdfRenderer;
    private final R2Service r2Service;
    private final ApplicationEventPublisher eventPublisher;
    private final ESignatureService esignatureService;

    // ==================== LADO COMERCIAL (PASOS 7-10) ====================

    @Override
    public ContractSummaryResponseDTO generate(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireGenerationReady(onboarding);

        CommercialDetails details = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException("CommercialDetails no encontrado para userId: " + userId, CommercialDetails.class));

        List<CommercialDocument> validatedDocs = documentRepository.findByOnboarding_IdAndStatus(
                onboarding.getId(), CommercialDocumentStatus.VALIDATED);

        String html = buildContractHtml(onboarding, details, validatedDocs);
        byte[] pdfBytes = pdfRenderer.renderToPdf(html);

        CommercialContract contract = contractRepository.findByOnboarding_Id(onboarding.getId())
                .orElseGet(() -> {
                    CommercialContract c = new CommercialContract();
                    c.setOnboarding(onboarding);
                    return c;
                });

        int nextVersion = contract.getId() != null ? contract.getVersion() + 1 : 1;
        String objectKey = "commercial-contracts/" + onboarding.getId() + "/contract-v" + nextVersion + ".pdf";
        r2Service.putPrivateObject(objectKey, pdfBytes, "application/pdf");

        contract.setObjectKey(objectKey);
        contract.setVersion(nextVersion);
        contract.setStatus(ContractStatus.PENDING_BUSINESS_REVIEW);
        contract.setGeneratedAt(ZonedDateTime.now());
        contract.setBusinessApprovedAt(null);
        contract.setAdminReviewerUserId(null);
        contract.setAdminReviewedAt(null);
        contract.setAdminDecisionNotes(null);
        CommercialContract saved = contractRepository.save(contract);

        onboarding.setCurrentStep(OnboardingStep.BUSINESS_REVIEW_PENDING);
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_CONTRACT_GENERATED",
                "Se generó el Contrato Marco v" + nextVersion + " para revisión del empresario.",
                Map.of("contractId", saved.getId(), "version", nextVersion));

        return toSummary(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractSummaryResponseDTO getCurrent(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        return toSummary(getContractByOnboardingOrThrow(onboarding.getId()));
    }

    @Override
    public ContractSummaryResponseDTO businessApprove(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        CommercialContract contract = getContractByOnboardingOrThrow(onboarding.getId());

        if (contract.getStatus() != ContractStatus.PENDING_BUSINESS_REVIEW) {
            throw new OnboardingStepException("El contrato no está pendiente de revisión del empresario.");
        }

        contract.setBusinessApprovedAt(ZonedDateTime.now());
        contract.setStatus(ContractStatus.PENDING_VERYGANA_REVIEW);
        contractRepository.save(contract);

        onboarding.setCurrentStep(OnboardingStep.VERYGANA_REVIEW_PENDING);
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_CONTRACT_APPROVED_BY_BUSINESS",
                "El empresario aprobó el Contrato Marco v" + contract.getVersion() + ". Pasa a revisión de VERYGANA.",
                Map.of("contractId", contract.getId(), "version", contract.getVersion()));

        return toSummary(contract);
    }

    @Override
    public OnboardingStep requestChanges(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        if (onboarding.getCurrentStep() != OnboardingStep.CONTRACT_PENDING) {
            throw new OnboardingStepException(
                    "Solo puede solicitar cambios antes de generar el contrato, revisando el resumen.");
        }
        // Ruta D (integración técnica) nunca pasó por selección de plan: regresa
        // directo a documentos en lugar de a un paso de plan que no le aplica.
        onboarding.setCurrentStep(onboarding.getRoute() == CommercialRoute.D
                ? OnboardingStep.DOCUMENTS_PENDING : OnboardingStep.PLAN_PENDING);
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_CONTRACT_CHANGES_REQUESTED",
                "El empresario solicitó regresar a corregir campos no jurídicos.", Map.of());

        log.info("Comercial userId={} solicitó cambios sobre su contrato", userId);
        return onboarding.getCurrentStep();
    }

    // ==================== LADO VERYGANA / COMPLIANCE (PASO 11) ====================

    @Override
    @Transactional(readOnly = true)
    public List<ContractReviewListItemDTO> listContracts(ContractStatus statusFilter) {
        List<CommercialContract> contracts = statusFilter != null
                ? contractRepository.findByStatus(statusFilter)
                : contractRepository.findByStatusIn(COMPLIANCE_RELEVANT_STATUSES);

        return contracts.stream()
                .map(c -> {
                    CommercialOnboarding onboarding = c.getOnboarding();
                    CommercialDetails details = onboarding.getCommercialDetails();
                    return new ContractReviewListItemDTO(
                            c.getId(),
                            details.getId(),
                            details.getCompanyName(),
                            details.getUser().getEmail(),
                            onboarding.getRoute(),
                            c.getVersion(),
                            c.getStatus(),
                            c.getGeneratedAt(),
                            c.getBusinessApprovedAt(),
                            c.getAdminReviewedAt(),
                            c.getEsignatureSignedAt());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ContractSummaryResponseDTO getForReview(Long contractId) {
        return toSummary(getContractOrThrow(contractId));
    }

    @Override
    public ContractSummaryResponseDTO approve(Long contractId, Long reviewerUserId) {
        CommercialContract contract = getContractOrThrow(contractId);
        requirePendingVeryganaReview(contract);

        contract.setStatus(ContractStatus.APPROVED);
        contract.setAdminReviewerUserId(reviewerUserId);
        contract.setAdminReviewedAt(ZonedDateTime.now());
        contract.setAdminDecisionNotes("Aprobado");
        contractRepository.save(contract);

        publishAudit(contract.getOnboarding().getCommercialDetails().getId(), "COMMERCIAL_CONTRACT_APPROVED_BY_VERYGANA",
                "VERYGANA aprobó el Contrato Marco v" + contract.getVersion() + ". Se envía a firma electrónica.",
                Map.of("contractId", contract.getId(), "reviewerUserId", reviewerUserId));

        // Misma transacción -> misma persistence context: requestSignature() recarga este
        // mismo `contract` (identity map de Hibernate), así que sus cambios (status
        // PENDING_SIGNATURE, envelopeId, etc.) ya quedan reflejados aquí sin recargarlo.
        esignatureService.requestSignature(contract.getId());

        return toSummary(contract);
    }

    @Override
    public ContractSummaryResponseDTO markSigned(Long contractId) {
        esignatureService.markSigned(contractId, ZonedDateTime.now());
        return toSummary(getContractOrThrow(contractId));
    }

    @Override
    public ContractSummaryResponseDTO reject(Long contractId, Long reviewerUserId, String reason, boolean documentsIssue) {
        CommercialContract contract = getContractOrThrow(contractId);
        requirePendingVeryganaReview(contract);

        contract.setStatus(ContractStatus.REJECTED);
        contract.setAdminReviewerUserId(reviewerUserId);
        contract.setAdminReviewedAt(ZonedDateTime.now());
        contract.setAdminDecisionNotes(reason);
        contractRepository.save(contract);

        CommercialOnboarding onboarding = contract.getOnboarding();
        if (documentsIssue) {
            onboarding.setDocumentsCompletedAt(null);
            onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
            onboardingRepository.save(onboarding);
        }

        publishAudit(onboarding.getCommercialDetails().getId(), "COMMERCIAL_CONTRACT_REJECTED_BY_VERYGANA",
                "VERYGANA rechazó el Contrato Marco v" + contract.getVersion() + ": " + reason,
                Map.of("contractId", contract.getId(), "reviewerUserId", reviewerUserId, "documentsIssue", documentsIssue));

        return toSummary(contract);
    }

    // ==================== GENERACIÓN DEL DOCUMENTO ====================

    private String buildContractHtml(CommercialOnboarding o, CommercialDetails details, List<CommercialDocument> docs) {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("onboardingId", String.valueOf(o.getId()));
        vars.put("version", String.valueOf(contractRepository.findByOnboarding_Id(o.getId())
                .map(CommercialContract::getVersion).map(v -> v + 1).orElse(1)));
        vars.put("generatedAt", ZonedDateTime.now().format(DATE_FMT));
        vars.put("route", o.getRoute() != null ? o.getRoute().name() : "");
        vars.put("routeExplanation", nullSafe(o.getRouteExplanation()));

        vars.put("companyName", nullSafe(details.getCompanyName()));
        vars.put("personType", o.getPersonType() != null ? o.getPersonType().name() : "");
        vars.put("nit", nullSafe(details.getNit()));
        vars.put("legalRepFullName", (nullSafe(o.getLegalRepFirstName()) + " " + nullSafe(o.getLegalRepLastName())).trim());
        vars.put("legalRepDocType", details.getLegalRepDocType() != null ? details.getLegalRepDocType().name() : "");
        vars.put("legalRepDocNumber", nullSafe(details.getLegalRepDocNumber()));
        vars.put("economicActivityDescription", nullSafe(o.getEconomicActivityDescription()));
        vars.put("address", nullSafe(o.getAddress()));

        vars.put("planName", o.getSelectedPlan() != null ? o.getSelectedPlan().getName() : "");
        vars.put("monthlyFeeFormatted", formatMoney(o.getMonthlyFeeCentsSnapshot()));
        vars.put("investmentRangeFormatted",
                formatInvestmentRange(o.getMinInvestmentCentsSnapshot(), o.getMaxInvestmentCentsSnapshot()));
        vars.put("investmentAmountFormatted", formatMoney(o.getInvestmentAmountCentsSnapshot()));
        vars.put("saleCommissionPct", String.valueOf(o.getSaleCommissionPctSnapshot()));
        vars.put("maxKeysPct", String.valueOf(o.getMaxKeysPctSnapshot()));
        vars.put("taxNote", nullSafe(o.getTaxNoteSnapshot()));
        vars.put("liquidationConditions", nullSafe(o.getLiquidationConditionsSnapshot()));

        vars.put("contractDurationMonths", o.getContractDurationMonths() != null
                ? o.getContractDurationMonths() + " meses" : "No aplica");

        vars.put("documentsListHtml", buildDocumentsListHtml(docs));

        vars.put("termsVersion", nullSafe(o.getTermsVersion()));
        vars.put("termsAcceptedAt", o.getTermsAcceptedAt() != null ? o.getTermsAcceptedAt().format(DATE_FMT) : "");
        vars.put("termsDocumentUrl", nullSafe(o.getTermsDocumentUrl()));

        return templateLoader.render("contrato-marco.html", vars);
    }

    private String buildDocumentsListHtml(List<CommercialDocument> docs) {
        if (docs.isEmpty()) {
            return "<p>No se cargaron documentos.</p>";
        }
        StringBuilder sb = new StringBuilder("<ul>");
        for (CommercialDocument d : docs) {
            sb.append("<li>").append(d.getDocumentType().name()).append(" — ")
                    .append(nullSafe(d.getOriginalFileName())).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private String formatMoney(Long cents) {
        if (cents == null) return "No aplica";
        long pesos = cents / 100;
        return "$ " + NumberFormat.getNumberInstance(CO_LOCALE).format(pesos) + " COP";
    }

    private String formatInvestmentRange(Long min, Long max) {
        if (min == null && max == null) return "No aplica (plan de tarifa fija)";
        if (max == null) return "Desde " + formatMoney(min);
        return formatMoney(min) + " – " + formatMoney(max);
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    // ==================== HELPERS ====================

    private void requireGenerationReady(CommercialOnboarding onboarding) {
        // Ruta D (integración técnica) no pasa por selección de plan: se negocia
        // directamente con un asesor, así que no se exige planAcceptedAt.
        if (onboarding.getRoute() != CommercialRoute.D && onboarding.getPlanAcceptedAt() == null) {
            throw new OnboardingStepException("Debe aceptar el plan antes de generar el contrato.");
        }
        if (onboarding.getDocumentsCompletedAt() == null) {
            throw new OnboardingStepException("Debe completar la carga documental antes de generar el contrato.");
        }
        contractRepository.findByOnboarding_Id(onboarding.getId()).ifPresent(c -> {
            if (c.getStatus() == ContractStatus.APPROVED || c.getStatus() == ContractStatus.PENDING_SIGNATURE
                    || c.getStatus() == ContractStatus.SIGNED) {
                throw new OnboardingStepException(
                        "El contrato ya fue aprobado por VERYGANA y no puede regenerarse. Contacte a soporte.");
            }
        });
    }

    private void requirePendingVeryganaReview(CommercialContract contract) {
        if (contract.getStatus() != ContractStatus.PENDING_VERYGANA_REVIEW) {
            throw new OnboardingStepException("El contrato no está pendiente de revisión de VERYGANA.");
        }
    }

    private CommercialOnboarding getOnboardingOrThrow(Long userId) {
        return onboardingRepository.findByCommercialDetails_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No existe un proceso de onboarding comercial para userId: " + userId, CommercialOnboarding.class));
    }

    private CommercialContract getContractByOnboardingOrThrow(Long onboardingId) {
        return contractRepository.findByOnboarding_Id(onboardingId)
                .orElseThrow(() -> new OnboardingStepException("Aún no se ha generado un contrato para este registro."));
    }

    private CommercialContract getContractOrThrow(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ObjectNotFoundException("Contrato no encontrado: " + contractId, CommercialContract.class));
    }

    private ContractSummaryResponseDTO toSummary(CommercialContract c) {
        String downloadUrl = r2Service.getPrivateObject(c.getObjectKey(), 300);
        List<CommercialDocumentResponseDTO> documents = documentRepository
                .findByOnboarding_IdAndStatusNot(c.getOnboarding().getId(), CommercialDocumentStatus.ORPHANED)
                .stream()
                .map(d -> new CommercialDocumentResponseDTO(
                        d.getId(), d.getDocumentType(), d.getOriginalFileName(), d.getSizeBytes(), d.getStatus(),
                        d.getUploadedAt(),
                        d.getStatus() == CommercialDocumentStatus.VALIDATED
                                ? r2Service.getPrivateObject(d.getObjectKey(), 300) : null))
                .toList();

        return new ContractSummaryResponseDTO(
                c.getId(), c.getVersion(), c.getStatus(), c.getGeneratedAt(),
                c.getBusinessApprovedAt(), c.getAdminReviewedAt(), c.getAdminDecisionNotes(),
                c.getEsignatureSentAt(), c.getEsignatureSignedAt(), downloadUrl, documents);
    }

    private void publishAudit(Long userId, String action, String description, Map<String, Object> additionalData) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(userId)
                    .action(action)
                    .level(AuditLevel.INFO)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(CommercialContractServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(additionalData)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }
}
