package com.verygana2.services.commercial;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.Municipality;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.services.LocationService;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;
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
    private final LocationService locationService;
    private final ApplicationEventPublisher eventPublisher;

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
    public CommercialOnboardingStatusResponseDTO acceptTerms(Long userId, TermsAcceptanceRequestDTO dto,
                                                               String ipAddress, String userAgent) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);

        onboarding.setTermsVersion(dto.getTermsVersion());
        onboarding.setTermsDocumentUrl(dto.getTermsDocumentUrl());
        onboarding.setTermsPublishedDate(dto.getTermsPublishedDate());
        onboarding.setTermsAcceptedAt(ZonedDateTime.now());
        onboarding.setTermsAcceptedIp(ipAddress);
        onboarding.setTermsAcceptedUserAgent(userAgent);

        if (onboarding.getCurrentStep() == OnboardingStep.TERMS_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.LEGAL_IDENTIFICATION_PENDING);
        }

        onboardingRepository.save(onboarding);

        publishAudit(userId, "TERMS_ACCEPTED",
                "Comercial aceptó Términos y Condiciones v" + dto.getTermsVersion(),
                ipAddress, userAgent,
                Map.of("termsVersion", dto.getTermsVersion(), "termsDocumentUrl", dto.getTermsDocumentUrl()));

        log.info("Comercial userId={} aceptó T&C v{}", userId, dto.getTermsVersion());
        return toStatusDTO(onboarding);
    }

    @Override
    public CommercialOnboardingStatusResponseDTO submitLegalIdentification(Long userId, LegalIdentificationRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireTermsAccepted(onboarding);

        if (onboarding.getLegalIdentificationCompletedAt() != null) {
            throw new OnboardingStepException(
                    "La identificación jurídica ya fue registrada y no puede modificarse desde el registro. "
                            + "Contacta a soporte si necesitas corregirla.");
        }

        onboarding.setPersonType(dto.getPersonType());
        onboarding.setLegalRepFullName(dto.getLegalRepFullName());
        onboarding.setEconomicActivityDescription(dto.getEconomicActivityDescription());
        onboarding.setAddress(dto.getAddress());
        onboarding.setLegalIdentificationCompletedAt(ZonedDateTime.now());

        if (onboarding.getCurrentStep() == OnboardingStep.LEGAL_IDENTIFICATION_PENDING) {
            onboarding.setCurrentStep(OnboardingStep.DIAGNOSTIC_PENDING);
        }

        CommercialDetails details = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ObjectNotFoundException("CommercialDetails no encontrado para userId: " + userId, CommercialDetails.class));

        details.setCompanyName(dto.getCompanyName());
        details.setNit(dto.getNit());
        details.setLegalRepDocType(dto.getLegalRepDocType());
        details.setLegalRepDocNumber(dto.getLegalRepDocNumber());
        if (dto.getCiiuCode() != null && !dto.getCiiuCode().isBlank()) {
            details.setCiiuCode(dto.getCiiuCode());
        }
        if (dto.getMunicipalityCode() != null && !dto.getMunicipalityCode().isBlank()) {
            Municipality municipality = locationService.getMunicipalityEntityByCode(dto.getMunicipalityCode());
            details.setMunicipality(municipality);
            details.setMunicipalityName(municipality.getName());
            details.setDepartmentName(municipality.getDepartment().getName());
        }
        commercialDetailsRepository.save(details);

        onboardingRepository.save(onboarding);

        publishAudit(userId, "LEGAL_IDENTIFICATION_SUBMITTED",
                "Comercial completó identificación jurídica (" + dto.getPersonType() + ")",
                null, null, Map.of("personType", dto.getPersonType().name(), "nit", dto.getNit()));

        return toStatusDTO(onboarding);
    }

    @Override
    public RouteClassificationResponseDTO submitDiagnostic(Long userId, CommercialDiagnosticRequestDTO dto) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireLegalIdentificationCompleted(onboarding);
        requireNotInBusinessReviewOrLater(onboarding);

        onboarding.setPrimaryGoal(dto.getPrimaryGoal());
        onboarding.setWantsFixedFee(dto.getWantsFixedFee());
        onboarding.setAcceptsCommissionOnSaleOnly(dto.getAcceptsCommissionOnSaleOnly());
        onboarding.setMaxPromotionalKeysPercentage(dto.getMaxPromotionalKeysPercentage());
        onboarding.setAcceptedCommissionPercentage(dto.getAcceptedCommissionPercentage());
        onboarding.setRequiresCustomGames(dto.getRequiresCustomGames());
        onboarding.setTechIntegrationNeeds(dto.getTechIntegrationNeeds() == null
                ? new HashSet<>() : new HashSet<>(dto.getTechIntegrationNeeds()));
        onboarding.setRegulatedSector(dto.getRegulatedSector());
        onboarding.setRequiresSpecialNegotiation(dto.getRequiresSpecialNegotiation());
        onboarding.setContractDurationMonths(dto.getContractDurationMonths());
        onboarding.setPaymentPeriodicity(dto.getPaymentPeriodicity());
        onboarding.setTerminationTerms(dto.getTerminationTerms());
        onboarding.setDiagnosticCompletedAt(ZonedDateTime.now());

        RouteClassificationResponseDTO classification = classify(onboarding);
        onboarding.setRoute(classification.getRoute());
        onboarding.setRouteExplanation(classification.getExplanation());
        onboarding.setClassifiedAt(ZonedDateTime.now());
        onboarding.setRouteConfirmed(false);
        onboarding.setRouteConfirmedAt(null);

        // La ruta pudo cambiar: cualquier plan ya aceptado queda invalidado y debe re-aceptarse.
        onboarding.setSelectedPlan(null);
        onboarding.setPlanAcceptedAt(null);
        onboarding.setCurrentStep(OnboardingStep.CLASSIFICATION_PENDING);

        onboardingRepository.save(onboarding);

        publishAudit(userId, "COMMERCIAL_DIAGNOSTIC_SUBMITTED",
                "Comercial completó diagnóstico comercial. Ruta calculada: " + classification.getRoute(),
                null, null, Map.of("route", classification.getRoute().name()));

        return classification;
    }

    @Override
    @Transactional(readOnly = true)
    public RouteClassificationResponseDTO getClassification(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        if (onboarding.getRoute() == null) {
            throw new OnboardingStepException("Aún no se ha completado el diagnóstico comercial, no hay una ruta asignada.");
        }
        return new RouteClassificationResponseDTO(
                onboarding.getRoute(), onboarding.getRoute().name(),
                onboarding.getRouteExplanation(), onboarding.isRouteConfirmed());
    }

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

    // ==================== PASO 6-7: PLAN Y RESUMEN ECONÓMICO ====================

    @Override
    @Transactional(readOnly = true)
    public PlanSummaryResponseDTO getRecommendedPlan(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireRouteConfirmed(onboarding);
        Plan plan = resolvePlanForRoute(onboarding.getRoute());
        return buildPlanSummary(onboarding, plan);
    }

    @Override
    public PlanSummaryResponseDTO acceptPlan(Long userId) {
        CommercialOnboarding onboarding = getOnboardingOrThrow(userId);
        requireRouteConfirmed(onboarding);
        requireNotInBusinessReviewOrLater(onboarding);

        Plan plan = resolvePlanForRoute(onboarding.getRoute());

        onboarding.setSelectedPlan(plan);
        onboarding.setRequiresAdvisorContact(requiresAdvisor(onboarding.getRoute()));
        onboarding.setMonthlyFeeCentsSnapshot(plan.getMonthlyPriceCents());
        onboarding.setMinInvestmentCentsSnapshot(plan.getMinInvestmentCents());
        onboarding.setMaxInvestmentCentsSnapshot(plan.getMaxInvestmentCents());
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
                "Comercial aceptó el plan " + plan.getCode() + " y sus condiciones económicas.",
                null, null, Map.of("planCode", plan.getCode().name(),
                        "saleCommissionPct", plan.getSaleCommissionPct()));

        return buildPlanSummary(onboarding, plan);
    }

    private Plan resolvePlanForRoute(CommercialRoute route) {
        Plan.PlanCode code = switch (route) {
            case A -> Plan.PlanCode.BASIC;
            case B -> Plan.PlanCode.STANDARD;
            case C, D, E -> Plan.PlanCode.PREMIUM;
        };
        return planRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ObjectNotFoundException("No hay un plan activo configurado para: " + code, Plan.class));
    }

    private boolean requiresAdvisor(CommercialRoute route) {
        return route == CommercialRoute.D || route == CommercialRoute.E;
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
                accepted ? onboarding.getSaleCommissionPctSnapshot() : plan.getSaleCommissionPct(),
                accepted ? onboarding.getMaxKeysPctSnapshot() : plan.getMaxKeysPct(),
                accepted ? onboarding.getTaxNoteSnapshot() : taxNote,
                accepted ? onboarding.getLiquidationConditionsSnapshot() : liquidationConditions,
                requiresAdvisor(onboarding.getRoute()),
                accepted,
                onboarding.getPlanAcceptedAt());
    }

    // ==================== CLASIFICACIÓN AUTOMÁTICA (RUTA A-E) ====================

    /**
     * Motor de reglas de clasificación. Evaluado en orden de prioridad: negociación
     * especial > sector regulado > requisitos técnicos/personalización > modelo de
     * tarifa fija > comisión estándar (caso por defecto).
     */
    private RouteClassificationResponseDTO classify(CommercialOnboarding o) {
        boolean specialNegotiation = Boolean.TRUE.equals(o.getRequiresSpecialNegotiation());
        boolean regulated = Boolean.TRUE.equals(o.getRegulatedSector());
        boolean needsTechOrCustomGames = Boolean.TRUE.equals(o.getRequiresCustomGames())
                || (o.getTechIntegrationNeeds() != null && !o.getTechIntegrationNeeds().isEmpty());
        boolean fixedFeeOnly = Boolean.TRUE.equals(o.getWantsFixedFee())
                && !Boolean.TRUE.equals(o.getAcceptsCommissionOnSaleOnly());

        CommercialRoute route;
        String explanation;

        if (specialNegotiation) {
            route = CommercialRoute.E;
            explanation = "Ruta E: su solicitud requiere negociación corporativa especial o aprobación previa. "
                    + "Un asesor comercial de VERYGANA se pondrá en contacto para definir condiciones a la medida.";
        } else if (regulated) {
            route = CommercialRoute.D;
            explanation = "Ruta D: su actividad pertenece a un sector regulado. Antes de activarse, su cuenta pasará "
                    + "por una validación adicional de cumplimiento normativo.";
        } else if (needsTechOrCustomGames) {
            route = CommercialRoute.C;
            explanation = "Ruta C: su operación requiere integración tecnológica (API, conciliación o activación "
                    + "automática) y/o juegos personalizados. El equipo técnico de VERYGANA coordinará la implementación.";
        } else if (fixedFeeOnly) {
            route = CommercialRoute.A;
            explanation = "Ruta A: pagará una tarifa fija con presencia en espacios generales de la plataforma. "
                    + "Es el camino de activación más simple y rápido.";
        } else {
            route = CommercialRoute.B;
            explanation = "Ruta B: pagará comisión únicamente cuando exista una venta, bajo el modelo estándar "
                    + "de VERYGANA, sin requisitos técnicos ni regulatorios especiales.";
        }

        return new RouteClassificationResponseDTO(route, route.name(), explanation, false);
    }

    // ==================== HELPERS ====================

    private CommercialOnboarding getOnboardingOrThrow(Long userId) {
        return onboardingRepository.findByUser_Id(userId)
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
                || step == OnboardingStep.COMPLETED) {
            throw new OnboardingStepException(
                    "No puede modificar esta información en este punto del proceso. "
                            + "Solicite cambios desde la revisión del contrato (POST /commercials/onboarding/contract/request-changes).");
        }
    }

    private CommercialOnboardingStatusResponseDTO toStatusDTO(CommercialOnboarding o) {
        RouteClassificationResponseDTO classification = o.getRoute() == null ? null
                : new RouteClassificationResponseDTO(o.getRoute(), o.getRoute().name(), o.getRouteExplanation(), o.isRouteConfirmed());

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
                o.getCurrentStep() == OnboardingStep.COMPLETED);
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
