package com.verygana2.services.commercial;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.onboarding.AcceptPlanRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationSummaryDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanComparisonResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanOptionDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.mappers.CommercialOnboardingMapper;
import com.verygana2.models.Municipality;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestion;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestionnaire;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.commercial.DiagnosticQuestionnaireRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.services.LocationService;
import com.verygana2.services.interfaces.commercial.CommercialDocumentService;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialOnboardingServiceImpl implements CommercialOnboardingService {

    private final CommercialOnboardingRepository onboardingRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final CommercialContractRepository commercialContractRepository;
    private final PlanRepository planRepository;
    private final ScreeningService screeningService;
    private final LegalDocumentRepository legalDocumentRepository;
    private final LocationService locationService;
    private final ApplicationEventPublisher eventPublisher;
    private final CommercialDocumentService documentService;
    private final CommercialOnboardingMapper commercialOnboardingMapper;
    private final PlanService planService;
    private final CommercialDiagnosticClassifier diagnosticClassifier;
    private final DiagnosticQuestionnaireRepository diagnosticQuestionnaireRepository;

    @Override
    @Transactional(readOnly = true)
    public CommercialOnboardingStatusResponseDTO getStatus(Long userId) {
        return toStatusDTO(getOnboardingOrThrow(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public CommercialOnboardingSummaryResponseDTO getSummary(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        CommercialDetails details = onboarding.getCommercialDetails();

        LegalIdentificationSummaryDTO legalIdentification = onboarding.getLegalIdentificationCompletedAt() == null ? null
                : commercialOnboardingMapper.toLegalIdentificationSummary(onboarding, details);

        PlanSummaryResponseDTO plan = onboarding.getSelectedPlan() == null ? null
                : buildPlanSummary(onboarding, onboarding.getSelectedPlan());

        return commercialOnboardingMapper.toSummaryDTO(
                onboarding, legalIdentification, plan, documentService.getStatus(userId));
    }

    // 1. ACEPTACIÓN DE TÉRMINOS Y CONDICIONES
    @Override
    public CommercialOnboardingStatusResponseDTO acceptTerms(Long userId, TermsAcceptanceRequestDTO dto,
                                                               String ipAddress, String userAgent) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);

        LegalDocument terms = legalDocumentRepository
                .findByTypeAndVersion(LegalDocumentType.BUSINESS_OWNER_TERMS_AND_CONDITIONS, dto.getTermsVersion())
                .orElseThrow(() -> new OnboardingStepException(
                        "La versión de Términos y Condiciones indicada no existe: " + dto.getTermsVersion()));

        commercialOnboardingMapper.applyTermsAcceptance(terms, ipAddress, userAgent, onboarding);
        onboarding.setTermsAcceptedAt(ZonedDateTime.now());

        if (onboarding.getCurrentStep() == OnboardingStep.TERMS_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.LEGAL_IDENTIFICATION_PENDING);
        }

        onboardingRepository.save(onboarding);

        publishAudit(userId, "TERMS_ACCEPTED",
                "Comercial aceptó Términos y Condiciones v" + dto.getTermsVersion(),
                ipAddress, userAgent,
                Map.of("termsVersion", terms.getVersion(), "termsDocumentUrl", terms.getDocumentUrl()));

        log.info("Comercial userId={} aceptó T&C v{}", userId, dto.getTermsVersion());
        return toStatusDTO(onboarding);
    }

    // 2. IDENTIFICACIÓN JURÍDICA
    @Override
    public CommercialOnboardingStatusResponseDTO submitLegalIdentification(Long userId, LegalIdentificationRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireTermsAccepted(onboarding);

        if (onboarding.getLegalIdentificationCompletedAt() != null) {
            throw new OnboardingStepException(
                    "La identificación jurídica ya fue registrada y no puede modificarse desde el registro. " + "Contacta a soporte si necesitas corregirla.");
        }

        commercialOnboardingMapper.applyLegalIdentificationToOnboarding(dto, onboarding);
        onboarding.setLegalIdentificationCompletedAt(ZonedDateTime.now());

        if (onboarding.getCurrentStep() == OnboardingStep.LEGAL_IDENTIFICATION_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.DIAGNOSTIC_PENDING);
        }

        CommercialDetails details = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException("CommercialDetails no encontrado para userId: " + userId, CommercialDetails.class));

        if (commercialDetailsRepository.existsByNit(dto.getNit())) {
            throw new OnboardingStepException("El NIT '" + dto.getNit() + "' ya está registrado por otra cuenta.");
        }
        if (dto.getMercantileRegistration() != null && !dto.getMercantileRegistration().isBlank()
                && commercialDetailsRepository.existsByMercantileRegistration(dto.getMercantileRegistration())) {
            throw new OnboardingStepException(
                    "La matrícula mercantil '" + dto.getMercantileRegistration() + "' ya está registrada por otra cuenta.");
        }

        String companyName = dto.getCompanyName();
        if (companyName == null || companyName.isBlank()) {
            if (dto.getPersonType() == PersonType.JURIDICA) {
                throw new OnboardingStepException("La razón social es requerida para persona jurídica.");
            }
            companyName = dto.getLegalRepFirstName() + " " + dto.getLegalRepLastName();
        }

        details.setCompanyName(companyName);
        commercialOnboardingMapper.applyLegalIdentificationToDetails(dto, details);
        if (dto.getCiiuCode() != null && !dto.getCiiuCode().isBlank()) {
            details.setCiiuCode(dto.getCiiuCode());
        }
        if (dto.getMunicipalityCode() != null && !dto.getMunicipalityCode().isBlank()) {
            Municipality municipality = locationService.getMunicipalityEntityByCode(dto.getMunicipalityCode());
            details.setMunicipality(municipality);
            details.setMunicipalityName(municipality.getName());
            details.setDepartmentName(municipality.getDepartment().getName());
        }
        try {
            commercialDetailsRepository.save(details);
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad ante condiciones de carrera: dos submits simultáneos con el
            // mismo NIT/matrícula mercantil pueden pasar ambos el chequeo existsBy* de arriba.
            throw new OnboardingStepException("El NIT o la matrícula mercantil ya están registrados por otra cuenta.");
        }

        // Screening SAGRILAFT de la empresa y del representante legal — solo puede
        // correr aquí, en el paso 3, porque es cuando estos datos existen por primera vez.
        screeningService.screenOrThrow(userId, companyName, dto.getNit());
        screeningService.screenOrThrow(userId,
                dto.getLegalRepFirstName() + " " + dto.getLegalRepLastName(), dto.getLegalRepDocNumber());

        onboardingRepository.save(onboarding);

        publishAudit(userId, "LEGAL_IDENTIFICATION_SUBMITTED",
                "Comercial completó identificación jurídica (" + dto.getPersonType() + ")",
                null, null, Map.of("personType", dto.getPersonType().name(), "nit", dto.getNit()));

        return toStatusDTO(onboarding);
    }

    // 3a. CATÁLOGO DEL CUESTIONARIO DE DIAGNÓSTICO (para renderizar el paso 4)
    @Override
    @Transactional(readOnly = true)
    public DiagnosticQuestionnaireResponseDTO getDiagnosticQuestionnaire() {
        DiagnosticQuestionnaire questionnaire = diagnosticQuestionnaireRepository
                .findFirstByActiveTrueOrderByVersionDesc()
                .orElseThrow(() -> new OnboardingStepException(
                        "El cuestionario de diagnóstico comercial no está disponible en este momento."));
        return toQuestionnaireDTO(questionnaire);
    }

    private DiagnosticQuestionnaireResponseDTO toQuestionnaireDTO(DiagnosticQuestionnaire q) {
        List<DiagnosticQuestionnaireResponseDTO.Section> sections = q.getSections().stream()
                .map(s -> new DiagnosticQuestionnaireResponseDTO.Section(
                        s.getCode(), s.getTitle(), s.getSubtitle(),
                        s.getQuestions().stream().map(this::toQuestionDTO).toList()))
                .toList();
        return new DiagnosticQuestionnaireResponseDTO(
                q.getVersion(), q.getOpeningMessage(), List.copyOf(q.getOpeningActions()), sections);
    }

    private DiagnosticQuestionnaireResponseDTO.Question toQuestionDTO(DiagnosticQuestion question) {
        DiagnosticQuestionnaireResponseDTO.Dependency dependsOn = question.getDependsOnQuestionCode() == null
                ? null
                : new DiagnosticQuestionnaireResponseDTO.Dependency(
                        question.getDependsOnQuestionCode(),
                        List.of(question.getDependsOnValues().split(",")));
        List<DiagnosticQuestionnaireResponseDTO.Option> options = question.getOptions().stream()
                .map(o -> new DiagnosticQuestionnaireResponseDTO.Option(o.getValue(), o.getLabel(), o.isExclusive()))
                .toList();
        return new DiagnosticQuestionnaireResponseDTO.Question(
                question.getCode(), question.getFieldName(), question.getText(), question.getHelpText(),
                question.getType(), question.isRequired(), question.getMaxSelections(), question.isOrdered(),
                dependsOn, options);
    }

    // 3b. DIAGNÓSTICO COMERCIAL: cuestionario → modalidad A/B/C, o ruta alternativa
    //     de integración técnica (techIntegrationNeeds) → Ruta D.
    @Override
    public RouteClassificationResponseDTO submitDiagnostic(Long userId, CommercialDiagnosticRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireLegalIdentificationCompleted(onboarding);
        requireNotInBusinessReviewOrLater(onboarding);
        validateDiagnostic(dto);

        commercialOnboardingMapper.applyDiagnostic(dto, onboarding);
        onboarding.setDiagnosticCompletedAt(ZonedDateTime.now());

        RouteClassificationResponseDTO classification = classify(onboarding);
        onboarding.setRoute(classification.getRoute());
        onboarding.setRouteExplanation(classification.getExplanation());
        onboarding.setRoutePreliminary(classification.isPreliminary());
        onboarding.setVerificationRequired(classification.isVerificationRequired());
        onboarding.setClassifiedAt(ZonedDateTime.now());

        // La ruta pudo cambiar: cualquier plan ya aceptado queda invalidado y debe re-aceptarse,
        // y cualquier negociación especial previa (de un ciclo D/E anterior) queda obsoleta.
        onboarding.setSelectedPlan(null);
        onboarding.setPlanAcceptedAt(null);
        onboarding.setRequiresSpecialNegotiation(false);
        onboarding.setSpecialNegotiationResolvedAt(null);
        onboarding.setSpecialNegotiationDetails(null);

        if (classification.getRoute() == CommercialRoute.D) {
            // Ruta D (integración técnica), vía la bifurcación techIntegrationNeeds: no hay
            // clasificación de modalidad que confirmar ni plan que aceptar, y tampoco
            // continúa por documentos/contrato/pago dentro de la plataforma — todo eso se
            // coordina manualmente por fuera con un asesor. Queda en ADVISOR_CONTACT_PENDING,
            // terminal para el wizard de onboarding.
            onboarding.setRouteConfirmed(true);
            onboarding.setRouteConfirmedAt(ZonedDateTime.now());
            onboarding.setRequiresSpecialNegotiation(true);
            onboarding.setCurrentStep(OnboardingStep.ADVISOR_CONTACT_PENDING);
        } else {
            onboarding.setRouteConfirmed(false);
            onboarding.setRouteConfirmedAt(null);
            onboarding.setCurrentStep(OnboardingStep.CLASSIFICATION_PENDING);
        }

        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_DIAGNOSTIC_SUBMITTED",
                "Comercial completó diagnóstico comercial. Ruta calculada: " + classification.getRoute(),
                null, null, Map.of("route", classification.getRoute().name()));

        return classification;
    }

    // 4. CONSULTAR CLASIFICACIÓN (RUTA + EXPLICACIÓN)
    @Override
    @Transactional(readOnly = true)
    public RouteClassificationResponseDTO getClassification(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        if (onboarding.getRoute() == null) {
            throw new OnboardingStepException("Aún no se ha completado el diagnóstico comercial, no hay una ruta asignada.");
        }
        return commercialOnboardingMapper.toRouteClassification(onboarding);
    }

    // 4b. EL EMPRESARIO CONFIRMA SU CLASIFICACIÓN Y AVANZA AL PASO 5 (PLAN)
    @Override
    public CommercialOnboardingStatusResponseDTO confirmClassification(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        if (onboarding.getRoute() == null) {
            throw new OnboardingStepException("Debe completar el diagnóstico comercial antes de confirmar la clasificación.");
        }

        onboarding.setRouteConfirmed(true);
        onboarding.setRouteConfirmedAt(ZonedDateTime.now());
        if (onboarding.getCurrentStep() == OnboardingStep.CLASSIFICATION_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.PLAN_PENDING);
        }
        onboardingRepository.save(onboarding);

        publishAudit(userId, "ROUTE_CLASSIFICATION_CONFIRMED",
                "Comercial confirmó su clasificación (Ruta " + onboarding.getRoute() + ").",
                null, null, Map.of("route", onboarding.getRoute().name()));

        log.info("Comercial userId={} confirmó su clasificación en ruta {}", userId, onboarding.getRoute());
        return toStatusDTO(onboarding);
    }

    // 5. CONSULTAR CATÁLOGO DE PLANES (PARA TABLA COMPARATIVA) Y CUÁL ES EL RECOMENDADO
    @Override
    @Transactional(readOnly = true)
    public PlanComparisonResponseDTO getRecommendedPlan(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireRouteConfirmed(onboarding);
        requireRouteSupportsPlan(onboarding);

        Plan.PlanCode recommendedCode = resolvePlanForRoute(onboarding.getRoute()).getCode();

        List<PlanOptionDTO> plans = planRepository.findAllByActiveTrue().stream()
                .sorted(Comparator.comparing(p -> p.getCode().ordinal()))
                .map(p -> commercialOnboardingMapper.toPlanOptionDTO(p, p.getCode() == recommendedCode, false))
                .toList();

        return new PlanComparisonResponseDTO(
                recommendedCode,
                isSpecialNegotiationPending(onboarding),
                plans);
    }

    // 5b. EL EMPRESARIO ACEPTA UN PLAN (NO NECESARIAMENTE EL RECOMENDADO) Y SUS CONDICIONES ECONÓMICAS
    @Override
    public PlanSummaryResponseDTO acceptPlan(Long userId, AcceptPlanRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireRouteConfirmed(onboarding);
        requireRouteSupportsPlan(onboarding);
        requireNotInBusinessReviewOrLater(onboarding);

        // El bloqueo es permanente, no solo mientras está pendiente: una vez que hubo
        // negociación especial (D o E), el plan queda fijo con lo que el asesor ya
        // confirmó — cambiarlo después de resuelto invalidaría ese acuerdo en silencio.
        if (Boolean.TRUE.equals(onboarding.getRequiresSpecialNegotiation())) {
            throw new OnboardingStepException(onboarding.getSpecialNegotiationResolvedAt() == null
                    ? "Su cuenta está en negociación con un asesor de VERYGANA; no puede cambiar de plan hasta que se resuelva."
                    : "Su cuenta ya tuvo una negociación especial resuelta por un asesor; no puede volver a cambiar de plan. Continúe generando el contrato.");
        }

        boolean specialNegotiation = Boolean.TRUE.equals(dto.getRequiresSpecialNegotiation());
        if (specialNegotiation
                && (dto.getSpecialNegotiationDetails() == null || dto.getSpecialNegotiationDetails().isBlank())) {
            throw new OnboardingStepException(
                    "Debe describir la negociación especial que necesita para que un asesor la evalúe.");
        }

        Plan plan = planRepository.findByCodeAndActiveTrue(dto.getPlanCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No hay un plan activo configurado para: " + dto.getPlanCode(), Plan.class));

        boolean isRecommended = plan.getCode() == resolvePlanForRoute(onboarding.getRoute()).getCode();
        Long investmentAmountCents = resolveInvestmentAmount(plan, dto.getInvestmentAmountCents());
        Integer contractDurationMonths = resolveContractDuration(plan, dto.getContractDurationMonths());

        if (specialNegotiation) {
            // Igual eligió un plan (el que más se ajuste), pero además necesita condiciones
            // a la medida: reclasifica a Ruta E y un asesor ajusta a partir de los detalles.
            onboarding.setRoute(CommercialRoute.E);
            onboarding.setRouteExplanation(
                    "Ruta E: su solicitud requiere negociación corporativa especial o aprobación previa. "
                            + "Un asesor comercial de VERYGANA se pondrá en contacto para definir condiciones a la medida.");
            onboarding.setClassifiedAt(ZonedDateTime.now());
        }
        onboarding.setRequiresSpecialNegotiation(specialNegotiation);
        onboarding.setSpecialNegotiationDetails(specialNegotiation ? dto.getSpecialNegotiationDetails() : null);

        onboarding.setSelectedPlan(plan);
        onboarding.setMonthlyFeeCentsSnapshot(plan.getMonthlyPriceCents());
        onboarding.setMinInvestmentCentsSnapshot(plan.getMinInvestmentCents());
        onboarding.setMaxInvestmentCentsSnapshot(plan.getMaxInvestmentCents());
        onboarding.setInvestmentAmountCentsSnapshot(investmentAmountCents);
        onboarding.setContractDurationMonths(contractDurationMonths);
        onboarding.setSaleCommissionPctSnapshot(plan.getSaleCommissionPct());
        onboarding.setMaxKeysPctSnapshot(plan.getMaxKeysPct());
        onboarding.setPlanAcceptedAt(ZonedDateTime.now());

        if (onboarding.getCurrentStep() == OnboardingStep.PLAN_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
        }
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_PLAN_ACCEPTED",
                "Comercial aceptó el plan " + plan.getCode() + " y sus condiciones económicas"
                        + (isRecommended ? " (recomendado)." : " (distinto al recomendado).")
                        + (specialNegotiation ? " Solicitó negociación especial (Ruta E)." : ""),
                null, null, Map.of("planCode", plan.getCode().name(),
                        "saleCommissionPct", plan.getSaleCommissionPct(),
                        "wasRecommended", isRecommended,
                        "requiresSpecialNegotiation", specialNegotiation));

        return buildPlanSummary(onboarding, plan);
    }

    private Plan resolvePlanForRoute(CommercialRoute route) {
        // Modalidades del "Insumo técnico de caracterización empresarial":
        // A = Empresa Tipo A, B = Empresa Tipo B, C = candidata a Empresa Premium.
        // D/E no se asignan desde el diagnóstico; si aparecen (asignación manual)
        // se tratan como Premium para la orientación de plan.
        Plan.PlanCode code = switch (route) {
            case A -> Plan.PlanCode.BASIC;
            case B -> Plan.PlanCode.STANDARD;
            case C, D, E -> Plan.PlanCode.PREMIUM;
        };
        return planRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ObjectNotFoundException("No hay un plan activo configurado para: " + code, Plan.class));
    }

    /**
     * true si compliance todavía no resolvió la negociación (integración técnica en
     * Ruta D, condiciones a la medida en Ruta E — ver resolveAdvisorNegotiation). Sin
     * el chequeo de specialNegotiationResolvedAt, esto mostraría "pendiente" para
     * siempre incluso ya resuelto: requiresSpecialNegotiation queda en true como
     * registro histórico de que la cuenta lo necesitó, no se resetea al resolver.
     */
    private boolean isSpecialNegotiationPending(CommercialOnboarding onboarding) {
        return Boolean.TRUE.equals(onboarding.getRequiresSpecialNegotiation())
                && onboarding.getSpecialNegotiationResolvedAt() == null;
    }

    private Long resolveInvestmentAmount(Plan plan, Long requestedAmountCents) {
        if (plan.getCode() == Plan.PlanCode.BASIC) {
            return null;
        }
        if (requestedAmountCents == null) {
            throw new OnboardingStepException(
                    "El monto a invertir es requerido para el plan " + plan.getCode() + ".");
        }
        if (plan.getMinInvestmentCents() != null && requestedAmountCents < plan.getMinInvestmentCents()) {
            throw new OnboardingStepException(
                    "El monto a invertir debe ser al menos $" + (plan.getMinInvestmentCents() / 100) + " COP para el plan " + plan.getCode() + ".");
        }
        if (plan.getMaxInvestmentCents() != null && requestedAmountCents > plan.getMaxInvestmentCents()) {
            throw new OnboardingStepException(
                    "El monto a invertir no debe superar $" + (plan.getMaxInvestmentCents() / 100) + " COP para el plan " + plan.getCode() + ".");
        }
        return requestedAmountCents;
    }

    private Integer resolveContractDuration(Plan plan, Integer requestedMonths) {
        if (plan.getCode() != Plan.PlanCode.BASIC) {
            return null;
        }
        if (requestedMonths == null || requestedMonths < 1) {
            throw new OnboardingStepException(
                    "La duración del contrato (en meses) es requerida para el plan " + plan.getCode() + ".");
        }
        return requestedMonths;
    }

    private PlanSummaryResponseDTO buildPlanSummary(CommercialOnboarding onboarding, Plan plan) {
        boolean accepted = onboarding.getPlanAcceptedAt() != null;
        return new PlanSummaryResponseDTO(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                accepted ? onboarding.getMonthlyFeeCentsSnapshot() : plan.getMonthlyPriceCents(),
                accepted ? onboarding.getMinInvestmentCentsSnapshot() : plan.getMinInvestmentCents(),
                accepted ? onboarding.getMaxInvestmentCentsSnapshot() : plan.getMaxInvestmentCents(),
                accepted ? onboarding.getInvestmentAmountCentsSnapshot() : null,
                accepted ? onboarding.getContractDurationMonths() : null,
                accepted ? onboarding.getSaleCommissionPctSnapshot() : plan.getSaleCommissionPct(),
                accepted ? onboarding.getMaxKeysPctSnapshot() : plan.getMaxKeysPct(),
                isSpecialNegotiationPending(onboarding),
                onboarding.getSpecialNegotiationResolvedAt(),
                onboarding.getSpecialNegotiationDetails(),
                accepted,
                onboarding.getPlanAcceptedAt());
    }

    private static boolean requestsTechIntegration(CommercialDiagnosticRequestDTO dto) {
        return dto.getTechIntegrationNeeds() != null && !dto.getTechIntegrationNeeds().isEmpty();
    }

    /**
     * Exige respondidas las preguntas del cuestionario de caracterización sin las
     * cuales la recomendación sería una adivinanza. El resto son adaptativas
     * (§2: "el sistema omitirá las que no correspondan") y el motor de reglas las
     * trata como no respondidas si vienen nulas.
     *
     * En la ruta alternativa de integración técnica no se valida el cuestionario:
     * solo se exige la descripción de la integración.
     */
    private void validateDiagnostic(CommercialDiagnosticRequestDTO dto) {
        if (requestsTechIntegration(dto)) {
            if (dto.getIntegrationDetails() == null || dto.getIntegrationDetails().isBlank()) {
                throw new OnboardingStepException(
                        "Debe describir la integración técnica que necesita para que un asesor la evalúe.");
            }
            return;
        }
        requireAnswered(dto.getMainActivity(), "la actividad principal de su empresa");
        requireAnswered(dto.getMarketReachStructure(), "quién hace posible que sus productos lleguen al mercado");
        requireAnswered(dto.getSellsDirectlyAndConcentrated(), "si vende directamente y concentra la operación");
        requireAnswered(dto.getDirectSaleToConsumer(), "si vende principalmente de manera directa al consumidor");
        requireAnswered(dto.getDesiredActiveOffers(), "cuántas ofertas desea mantener activas");
        requireAnswered(dto.getMetricsNeeded(), "qué información necesita para decidir");
        requireAnswered(dto.getIndependentEntrepreneursHelp(), "si otros empresarios independientes ayudan a llevar sus productos al consumidor");
        requireAnswered(dto.getTypeAMonthlyFeeViable(), "si considera viable la cuota mensual de Tipo A");
        requireAnswered(dto.getTypeBInvestmentCapacity(), "si está en capacidad de realizar una inversión Tipo B");
        requireAnswered(dto.getAcceptsPremiumBrandFocus(), "si acepta el enfoque de marca de la modalidad Premium");
    }

    private void requireAnswered(Object answer, String what) {
        if (answer == null) {
            throw new OnboardingStepException("Debe responder " + what + " para completar el diagnóstico.");
        }
    }

    // ==================== CLASIFICACIÓN AUTOMÁTICA (MODALIDAD A/B/C o RUTA D) ====================

    /**
     * Ruta alternativa de integración técnica ({@code techIntegrationNeeds} no
     * vacío) → Ruta D: proveedor/aliado cuya implementación y condiciones económicas
     * las coordina un asesor de VERYGANA por fuera de la plataforma. En caso
     * contrario delega en {@link CommercialDiagnosticClassifier}, que aplica las
     * "Reglas decisivas de clasificación" (§13) y las "Reglas de recomendación"
     * (§14) del insumo técnico y siempre devuelve A, B o C. La Ruta E (negociación
     * especial) no se calcula aquí: el empresario la solicita en acceptPlan().
     */
    private RouteClassificationResponseDTO classify(CommercialOnboarding o) {
        if (o.getTechIntegrationNeeds() != null && !o.getTechIntegrationNeeds().isEmpty()) {
            return new RouteClassificationResponseDTO(
                    CommercialRoute.D, CommercialRoute.D.name(), "Integración técnica",
                    "El equipo técnico de VERYGANA coordinará la implementación y las condiciones "
                            + "económicas se definirán según el tipo de integración.",
                    false, false, false);
        }
        CommercialDiagnosticClassifier.Result r = diagnosticClassifier.classify(
                o.getDiagnosticAnswers(),
                o.getBusinessGoals(),
                o.getInstitutionalTools(),
                o.getCommercialNetworkActors(),
                o.getGrowthTools());
        return new RouteClassificationResponseDTO(
                r.route(), r.route().name(), r.modalityLabel(), r.explanation(),
                r.preliminary(), r.verificationRequired(), false);
    }

    // ==================== HELPERS ====================

    private CommercialOnboarding getOnboardingOrThrow(Long userId) {
        return onboardingRepository.findByCommercialDetails_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No existe un proceso de onboarding comercial para userId: " + userId, CommercialOnboarding.class));
    }

    private void requireTermsAccepted(CommercialOnboarding onboarding) {
        if (onboarding.getTermsAcceptedAt() == null) {
            throw new OnboardingStepException("Debe aceptar los Términos y Condiciones antes de continuar.");
        }
    }

    private void requireLegalIdentificationCompleted(CommercialOnboarding onboarding) {
        if (onboarding.getLegalIdentificationCompletedAt() == null) {
            throw new OnboardingStepException("Debe completar la identificación jurídica antes de continuar.");
        }
    }

    private void requireRouteConfirmed(CommercialOnboarding onboarding) {
        if (!onboarding.isRouteConfirmed()) {
            throw new OnboardingStepException("Debe confirmar su clasificación de ruta antes de continuar.");
        }
    }

    /**
     * Ruta D (integración técnica) no selecciona plan dentro de la plataforma: todo
     * se coordina manualmente con un asesor de VERYGANA (ver submitDiagnostic).
     */
    private void requireRouteSupportsPlan(CommercialOnboarding onboarding) {
        if (onboarding.getRoute() == CommercialRoute.D) {
            throw new OnboardingStepException(
                    "Su ruta de integración técnica no requiere seleccionar un plan; un asesor de VERYGANA "
                            + "se pondrá en contacto para coordinar las condiciones directamente.");
        }
    }

    /**
     * A partir de BUSINESS_REVIEW_PENDING el contrato ya fue generado y está en
     * revisión: los pasos "no jurídicos" (diagnóstico, plan) quedan bloqueados y
     * solo pueden reabrirse explícitamente vía POST /contract/request-changes.
     */
    private void requireNotInBusinessReviewOrLater(CommercialOnboarding onboarding) {
        OnboardingStep step = onboarding.getCurrentStep();
        if (step == OnboardingStep.BUSINESS_REVIEW_PENDING
                || step == OnboardingStep.VERYGANA_REVIEW_PENDING
                || step == OnboardingStep.SIGNATURE_PENDING
                || step == OnboardingStep.PAYMENT_PENDING
                || step == OnboardingStep.COMPLETED) {
            throw new OnboardingStepException(
                    "No puede modificar esta información en este punto del proceso. "
                            + "Solicite cambios desde la revisión del contrato (POST /commercials/onboarding/contract/request-changes).");
        }
    }

    private CommercialOnboardingStatusResponseDTO toStatusDTO(CommercialOnboarding o) {
        RouteClassificationResponseDTO classification = o.getRoute() == null ? null
                : commercialOnboardingMapper.toRouteClassification(o);

        CommercialContract contract = commercialContractRepository.findByOnboarding_Id(o.getId()).orElse(null);

        return new CommercialOnboardingStatusResponseDTO(
                o.getCurrentStep(),
                o.getTermsAcceptedAt() != null,
                o.getLegalIdentificationCompletedAt() != null,
                o.getDiagnosticCompletedAt() != null,
                o.getRoute() != null,
                o.isRouteConfirmed(),
                classification,
                o.getPlanAcceptedAt() != null,
                isSpecialNegotiationPending(o),
                o.getDocumentsCompletedAt() != null,
                contract != null,
                contract != null ? contract.getStatus() : null,
                contract != null && contract.getBusinessApprovedAt() != null,
                contract != null && contract.getAdminReviewedAt() != null,
                o.getCurrentStep() == OnboardingStep.COMPLETED,
                contract != null && contract.getStatus() == ContractStatus.REJECTED
                        ? contract.getAdminDecisionNotes() : null,
                contract != null && contract.getStatus() == ContractStatus.REJECTED
                        ? contract.getAdminReviewedAt() : null);
    }

    private void publishAudit(Long userId, String action, String description, String ip, String userAgent,
                               Map<String, Object> additionalData) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .userId(userId)
                    .action(action)
                    .level(AuditLevel.INFO)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(CommercialOnboardingServiceImpl.class.getName())
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(additionalData)
                    .build();
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }

    // 8. PASO 12: PAGO DE ACTIVACIÓN
    @Override
    public WompiCheckoutResponseDTO initiatePayment(Long commercialId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(commercialId);

        if (onboarding.getCurrentStep() != OnboardingStep.PAYMENT_PENDING) {
            throw new OnboardingStepException("Debe completar y firmar el Contrato Marco antes de realizar el pago de activación.");
        }

        Plan plan = onboarding.getSelectedPlan();
        if (plan == null) {
            throw new OnboardingStepException("No hay un plan aceptado para procesar el pago.");
        }

        // El monto sale de lo que el comercial ya aceptó y firmó, nunca del cliente:
        // BASIC tiene tarifa fija (PlanService la resuelve solo); STANDARD/PREMIUM usan
        // el monto de inversión congelado en acceptPlan().
        Long amountCents = plan.getCode() == Plan.PlanCode.BASIC
                ? null
                : onboarding.getInvestmentAmountCentsSnapshot();

        WompiCheckoutResponseDTO checkout = planService.initiatePlanPayment(
                onboarding.getCommercialDetails(), plan.getCode(), amountCents);

        publishAudit(commercialId, "COMMERCIAL_ONBOARDING_PAYMENT_INITIATED",
                "Comercial inició el pago de activación de su registro (plan " + plan.getCode() + ").",
                null, null, Map.of("planCode", plan.getCode().name()));

        log.info("Comercial userId={} inició el pago de activación (plan {})", commercialId, plan.getCode());
        return checkout;
    }
}
