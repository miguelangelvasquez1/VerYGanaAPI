package com.verygana2.mappers;

import java.math.BigDecimal;
import java.util.HashSet;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDocumentsStatusResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticSummaryDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.LegalIdentificationSummaryDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanOptionDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.userDetails.CommercialDetails;

/**
 * Mapper para CommercialOnboardingServiceImpl. Cubre los métodos que copian muchos
 * campos 1:1 entre DTOs y entidades (aceptación de términos, identificación jurídica,
 * diagnóstico, resúmenes de solo lectura). Los métodos con lógica condicional real
 * (buildPlanSummary, toStatusDTO — "snapshot si fue aceptado, valor vivo si no",
 * derivar booleans de un contrato nullable) se quedan como código manual en el
 * servicio: forzarlos a un mapper solo cambiaría ifs legibles por expresiones
 * MapStruct igual de largas y más difíciles de depurar.
 */
@Mapper(componentModel = "spring", imports = { PlanFeature.class, BigDecimal.class, HashSet.class })
public interface CommercialOnboardingMapper {

    // ===== Paso 1: Términos y condiciones =====

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "termsVersion", source = "terms.version")
    @Mapping(target = "termsDocumentUrl", source = "terms.documentUrl")
    @Mapping(target = "termsPublishedDate", source = "terms.publishedDate")
    @Mapping(target = "termsAcceptedIp", source = "ipAddress")
    @Mapping(target = "termsAcceptedUserAgent", source = "userAgent")
    void applyTermsAcceptance(LegalDocument terms, String ipAddress, String userAgent,
            @MappingTarget CommercialOnboarding onboarding);

    // ===== Paso 2: Identificación jurídica =====
    // Se divide en dos métodos porque un mismo DTO alimenta dos entidades distintas
    // (CommercialOnboarding y CommercialDetails). Los campos con derivación condicional
    // (companyName, ciiuCode, municipality*) se quedan manuales en el servicio.

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void applyLegalIdentificationToOnboarding(LegalIdentificationRequestDTO dto, @MappingTarget CommercialOnboarding onboarding);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "pep", expression = "java(Boolean.TRUE.equals(dto.getLegalRepPepDeclaration()))")
    void applyLegalIdentificationToDetails(LegalIdentificationRequestDTO dto, @MappingTarget CommercialDetails details);

    // ===== Paso 3: Diagnóstico comercial =====

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "techIntegrationNeeds",
            expression = "java(dto.getTechIntegrationNeeds() == null ? new HashSet<>() : new HashSet<>(dto.getTechIntegrationNeeds()))")
    void applyDiagnostic(CommercialDiagnosticRequestDTO dto, @MappingTarget CommercialOnboarding onboarding);

    // ===== Resúmenes de solo lectura =====

    @Mapping(target = "legalRepPepDeclaration", source = "details.pep")
    LegalIdentificationSummaryDTO toLegalIdentificationSummary(CommercialOnboarding onboarding, CommercialDetails details);

    DiagnosticSummaryDTO toDiagnosticSummary(CommercialOnboarding onboarding);

    @Mapping(target = "explanation", source = "routeExplanation")
    @Mapping(target = "confirmed", source = "routeConfirmed")
    @Mapping(target = "routeLabel", expression = "java(onboarding.getRoute() != null ? onboarding.getRoute().name() : null)")
    RouteClassificationResponseDTO toRouteClassification(CommercialOnboarding onboarding);

    /** Arma una fila del catálogo comparativo a partir del plan y sus features dinámicas. */
    @Mapping(target = "planCode", source = "plan.code")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "recommended", source = "recommended")
    @Mapping(target = "monthlyFeeCents", source = "plan.monthlyPriceCents")
    @Mapping(target = "canAdvertise", expression = "java(plan.getBoolFeature(\"CAN_ADVERTISE\", false))")
    @Mapping(target = "canUseGames", expression = "java(plan.getBoolFeature(\"CAN_USE_GAMES\", false))")
    @Mapping(target = "canUseSurveys", expression = "java(plan.getBoolFeature(\"CAN_USE_SURVEYS\", false))")
    @Mapping(target = "canHavePets", expression = "java(plan.getBoolFeature(\"CAN_HAVE_PETS\", false))")
    @Mapping(target = "maxProducts", expression = "java(plan.getIntFeature(\"MAX_PRODUCTS\", 0))")
    @Mapping(target = "maxAds", expression = "java(plan.getIntFeature(\"MAX_ADS\", 0))")
    @Mapping(target = "maxBrandedGames", expression = "java(plan.getIntFeature(\"MAX_BRANDED_GAMES\", 0))")
    @Mapping(target = "maxSurveys", expression = "java(plan.getIntFeature(\"MAX_SURVEYS\", 0))")
    @Mapping(target = "visibilityBoostPct",
            expression = "java(plan.getFeatureValue(\"VISIBILITY_BOOST\").map(PlanFeature::getDecimalValue).orElse(BigDecimal.ZERO))")
    PlanOptionDTO toPlanOptionDTO(Plan plan, boolean recommended);

    @Mapping(target = "termsVersion", source = "onboarding.termsVersion")
    @Mapping(target = "termsAcceptedAt", source = "onboarding.termsAcceptedAt")
    @Mapping(target = "documents", source = "documentsStatus")
    CommercialOnboardingSummaryResponseDTO toSummaryDTO(
            CommercialOnboarding onboarding,
            LegalIdentificationSummaryDTO legalIdentification,
            DiagnosticSummaryDTO diagnostic,
            RouteClassificationResponseDTO classification,
            PlanSummaryResponseDTO plan,
            CommercialDocumentsStatusResponseDTO documentsStatus);
}
