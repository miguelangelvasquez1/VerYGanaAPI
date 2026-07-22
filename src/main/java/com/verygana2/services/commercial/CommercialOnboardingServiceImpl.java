package com.verygana2.services.commercial;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.onboarding.AcceptPlanRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticSummaryDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationSummaryDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanComparisonResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanOptionDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.mappers.CommercialOnboardingMapper;
import com.verygana2.models.Municipality;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.services.LocationService;
import com.verygana2.services.interfaces.commercial.CommercialDocumentService;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
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

    @Value("${commercial.onboarding.economic-summary.tax-note}")
    private String taxNote;

    @Value("${commercial.onboarding.economic-summary.liquidation-conditions}")
    private String liquidationConditions;

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

        DiagnosticSummaryDTO diagnostic = onboarding.getDiagnosticCompletedAt() == null ? null
                : commercialOnboardingMapper.toDiagnosticSummary(onboarding);

        RouteClassificationResponseDTO classification = onboarding.getRoute() == null ? null
                : commercialOnboardingMapper.toRouteClassification(onboarding);

        PlanSummaryResponseDTO plan = onboarding.getSelectedPlan() == null ? null
                : buildPlanSummary(onboarding, onboarding.getSelectedPlan());

        return commercialOnboardingMapper.toSummaryDTO(
                onboarding, legalIdentification, diagnostic, classification, plan, documentService.getStatus(userId));
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

    // 3. DIAGNÓSTICO COMERCIAL Y CLASIFICACIÓN AUTOMÁTICA DE RUTA (A-E)
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
        onboarding.setClassifiedAt(ZonedDateTime.now());

        // La ruta pudo cambiar: cualquier plan ya aceptado queda invalidado y debe re-aceptarse.
        onboarding.setSelectedPlan(null);
        onboarding.setPlanAcceptedAt(null);

        if (classification.getRoute() == CommercialRoute.D) {
            // Ruta D (integración técnica): no hay clasificación que confirmar ni plan que
            // aceptar, se resuelve por negociación directa con un asesor. Se salta derecho
            // a la carga documental.
            onboarding.setRouteConfirmed(true);
            onboarding.setRouteConfirmedAt(ZonedDateTime.now());
            onboarding.setRequiresAdvisorContact(true);
            onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
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

        Plan.PlanCode recommendedCode = resolvePlanForRoute(onboarding.getRoute()).getCode();

        List<PlanOptionDTO> plans = planRepository.findAllByActiveTrue().stream()
                .sorted(Comparator.comparing(p -> p.getCode().ordinal()))
                .map(p -> commercialOnboardingMapper.toPlanOptionDTO(p, p.getCode() == recommendedCode))
                .toList();

        return new PlanComparisonResponseDTO(
                recommendedCode,
                requiresAdvisor(onboarding),
                taxNote,
                liquidationConditions,
                plans);
    }

    // 5b. EL EMPRESARIO ACEPTA UN PLAN (NO NECESARIAMENTE EL RECOMENDADO) Y SUS CONDICIONES ECONÓMICAS
    @Override
    public PlanSummaryResponseDTO acceptPlan(Long userId, AcceptPlanRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireRouteConfirmed(onboarding);
        requireNotInBusinessReviewOrLater(onboarding);

        Plan plan = planRepository.findByCodeAndActiveTrue(dto.getPlanCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No hay un plan activo configurado para: " + dto.getPlanCode(), Plan.class));

        boolean isRecommended = plan.getCode() == resolvePlanForRoute(onboarding.getRoute()).getCode();
        Long investmentAmountCents = resolveInvestmentAmount(plan, dto.getInvestmentAmountCents());
        Integer contractDurationMonths = resolveContractDuration(plan, dto.getContractDurationMonths());

        onboarding.setSelectedPlan(plan);
        onboarding.setRequiresAdvisorContact(requiresAdvisor(onboarding));
        onboarding.setMonthlyFeeCentsSnapshot(plan.getMonthlyPriceCents());
        onboarding.setMinInvestmentCentsSnapshot(plan.getMinInvestmentCents());
        onboarding.setMaxInvestmentCentsSnapshot(plan.getMaxInvestmentCents());
        onboarding.setInvestmentAmountCentsSnapshot(investmentAmountCents);
        onboarding.setContractDurationMonths(contractDurationMonths);
        onboarding.setSaleCommissionPctSnapshot(plan.getSaleCommissionPct());
        onboarding.setMaxKeysPctSnapshot(plan.getMaxKeysPct());
        onboarding.setTaxNoteSnapshot(taxNote);
        onboarding.setLiquidationConditionsSnapshot(liquidationConditions);
        onboarding.setPlanAcceptedAt(ZonedDateTime.now());

        if (onboarding.getCurrentStep() == OnboardingStep.PLAN_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
        }
        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_PLAN_ACCEPTED",
                "Comercial aceptó el plan " + plan.getCode() + " y sus condiciones económicas"
                        + (isRecommended ? " (recomendado)." : " (distinto al recomendado)."),
                null, null, Map.of("planCode", plan.getCode().name(),
                        "saleCommissionPct", plan.getSaleCommissionPct(),
                        "wasRecommended", isRecommended));

        return buildPlanSummary(onboarding, plan);
    }

    private Plan resolvePlanForRoute(CommercialRoute route) {
        Plan.PlanCode code = switch (route) {
            case A -> Plan.PlanCode.BASIC;
            case C -> Plan.PlanCode.STANDARD;
            case B, D, E -> Plan.PlanCode.PREMIUM;
        };
        return planRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ObjectNotFoundException("No hay un plan activo configurado para: " + code, Plan.class));
    }

    /** Rutas D/E siempre requieren asesor; sector regulado también, por la validación de cumplimiento adicional. */
    private boolean requiresAdvisor(CommercialOnboarding onboarding) {
        return onboarding.getRoute() == CommercialRoute.D
                || onboarding.getRoute() == CommercialRoute.E
                || Boolean.TRUE.equals(onboarding.getRegulatedSector());
    }

    /**
     * BASIC es suscripción mensual fija: el monto a invertir no aplica y se ignora
     * si se envía. STANDARD/PREMIUM sí lo requieren, y debe caer dentro del rango
     * de inversión del plan (PREMIUM no tiene techo, así que ahí solo se valida el mínimo).
     */
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

    /**
     * BASIC es suscripción con tarifa fija recurrente: la duración del contrato aplica
     * y es requerida. STANDARD/PREMIUM no la piden — el monto invertido se consume vía
     * comisión a un ritmo que depende de las ventas, no de un plazo fijo — así que se ignora.
     */
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
                accepted ? onboarding.getTaxNoteSnapshot() : taxNote,
                accepted ? onboarding.getLiquidationConditionsSnapshot() : liquidationConditions,
                requiresAdvisor(onboarding),
                accepted,
                onboarding.getPlanAcceptedAt());
    }

    /**
     * Q9 (techIntegrationNeeds) es una pregunta de bifurcación: si viene marcada, el resto
     * del diagnóstico es irrelevante (la ruta D se resuelve por negociación directa con un
     * asesor, sin plan), así que solo se exige integrationDetails. Si no viene marcada, se
     * exige el resto de las preguntas como antes.
     */
    private void validateDiagnostic(CommercialDiagnosticRequestDTO dto) {
        boolean needsTechIntegration = dto.getTechIntegrationNeeds() != null && !dto.getTechIntegrationNeeds().isEmpty();

        if (needsTechIntegration) {
            if (dto.getIntegrationDetails() == null || dto.getIntegrationDetails().isBlank()) {
                throw new OnboardingStepException(
                        "Debe describir la integración técnica que necesita para que un asesor la evalúe.");
            }
            return;
        }

        if (dto.getPrimaryGoal() == null) {
            throw new OnboardingStepException("El objetivo principal es requerido");
        }
        if (dto.getWantsFixedFee() == null) {
            throw new OnboardingStepException("Debe indicar si desea pagar una tarifa fija");
        }
        if (dto.getRequiresCustomGames() == null) {
            throw new OnboardingStepException("Debe indicar si requiere juegos personalizados");
        }
        if (dto.getRegulatedSector() == null) {
            throw new OnboardingStepException("Debe indicar si la actividad pertenece a un sector regulado");
        }
        if (dto.getRequiresSpecialNegotiation() == null) {
            throw new OnboardingStepException(
                    "Debe indicar si requiere negociación especial o aprobación corporativa previa");
        }
    }

    // ==================== CLASIFICACIÓN AUTOMÁTICA (RUTA A-E) ====================

    /**
     * Motor de reglas de clasificación. Evaluado en orden de prioridad: negociación
     * especial > integración técnica > personalización (Básico no soporta juegos:
     * CAN_USE_GAMES=false en PlanDataInitializer, así que quien los pide siempre va
     * a un plan de inversión con esa capacidad) > tarifa fija (solo Básico tiene una
     * tarifa fija real) > objetivo de venta > caso por defecto. Sector regulado no
     * cambia la ruta pero sí exige asesor y el permiso sectorial (ver requiresAdvisor
     * y CommercialDocumentServiceImpl).
     */
    private RouteClassificationResponseDTO classify(CommercialOnboarding o) {
        boolean specialNegotiation = Boolean.TRUE.equals(o.getRequiresSpecialNegotiation());
        boolean needsTechIntegration = o.getTechIntegrationNeeds() != null && !o.getTechIntegrationNeeds().isEmpty();
        boolean sells = o.getPrimaryGoal() == PrimaryGoal.VENDER || o.getPrimaryGoal() == PrimaryGoal.AMBAS;
        boolean visibilityGoal = o.getPrimaryGoal() == PrimaryGoal.PUBLICIDAD;
        boolean fixedFee = Boolean.TRUE.equals(o.getWantsFixedFee());
        boolean customGames = Boolean.TRUE.equals(o.getRequiresCustomGames());
        boolean regulated = Boolean.TRUE.equals(o.getRegulatedSector());

        CommercialRoute route;
        String explanation;

        if (specialNegotiation) {
            route = CommercialRoute.E;
            explanation = "Ruta E: su solicitud requiere negociación corporativa especial o aprobación previa. "
                    + "Un asesor comercial de VERYGANA se pondrá en contacto para definir condiciones a la medida. "
                    + "Por ahora, selecciona el plan que mas se ajuste a tus necesidades.";
        } else if (needsTechIntegration) {
            route = CommercialRoute.D;
            explanation = "Ruta D: es un proveedor o aliado de servicios que requiere integración técnica "
                    + "(API, conciliación o activación automática). El equipo técnico de VERYGANA coordinará "
                    + "la implementación y las condiciones económicas se definirán según el tipo de integración.";
        } else if (customGames) {
            // El plan Básico no soporta juegos (CAN_USE_GAMES=false): quien los necesita
            // siempre se asigna a un plan de inversión con esa capacidad, venda o no.
            route = CommercialRoute.B;
            explanation = "Ruta B: requiere juegos personalizados en su operación. El plan Básico no incluye "
                    + "esta función, así que se asigna un plan de inversión con soporte completo de gamificación.";
        } else if (fixedFee && sells) {
            // Plan Básico: única opción con tarifa fija mensual real (ver PlanDataInitializer).
            route = CommercialRoute.A;
            explanation = "Ruta A: paga una tarifa fija y vende directamente en la plataforma. "
                    + "Es el camino de activación más simple y rápido.";
        } else if (!sells) {
            route = CommercialRoute.C;
            explanation = visibilityGoal
                    ? "Ruta C: es una gran marca enfocada en visibilidad. No paga tarifa fija sino una "
                            + "inversión dentro del plan asignado, y normalmente no vende directamente en la plataforma."
                    : "Ruta C: su objetivo principal no es la venta directa, por lo que se clasifica como "
                            + "marca de visibilidad.";
        } else {
            route = CommercialRoute.B;
            explanation = "Ruta B: vende en la plataforma bajo el modelo estándar de VERYGANA, sin tarifa "
                    + "fija ni requisitos técnicos especiales.";
        }

        if (regulated) {
            explanation += " Al pertenecer a un sector regulado, deberá cargar el permiso sectorial "
                    + "correspondiente y su cuenta pasará por una validación de cumplimiento adicional.";
        }

        return new RouteClassificationResponseDTO(route, route.name(), explanation, false);
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
}
