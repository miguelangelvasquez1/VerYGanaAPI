package com.verygana2.services.inforequests;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertReviewRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertView;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.inforequests.ComplianceAlert;
import com.verygana2.models.inforequests.ComplianceAlertRule;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRuleRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.inforequests.ComplianceAlertService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComplianceAlertServiceImpl implements ComplianceAlertService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final String SCOPE_NOTICE = "Solo incluye operaciones en VERYGANA: no refleja actividades externas ni es "
            + "una evaluación de obligaciones tributarias. Es informativa y no constituye asesoría profesional.";

    private final ComplianceAlertRuleRepository ruleRepository;
    private final ComplianceAlertRepository alertRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final CommercialOnboardingRepository onboardingRepository;
    private final PurchaseItemService purchaseItemService;
    private final UserIdResolver userIdResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AlertRuleView> listRules() {
        return ruleRepository.findAllByOrderByCodeAscVersionDesc().stream().map(InformationRequestViews::rule).toList();
    }

    @Override
    public AlertRuleView createRule(AlertRuleRequest dto, Long userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("windowDays", dto.getWindowDays());
        params.put("minGrowthPct", dto.getMinGrowthPct());
        params.put("minCurrentSalesCents", dto.getMinCurrentSalesCents());

        int version = ruleRepository.findFirstByCodeOrderByVersionDesc(dto.getCode())
                .map(r -> r.getVersion() + 1).orElse(1);
        ruleRepository.findAllByOrderByCodeAscVersionDesc().stream()
                .filter(r -> r.getCode().equals(dto.getCode()) && r.isActive())
                .forEach(r -> {
                    r.setActive(false);
                    ruleRepository.save(r);
                });

        ComplianceAlertRule rule = new ComplianceAlertRule();
        rule.setCode(dto.getCode());
        rule.setVersion(version);
        rule.setRuleType(dto.getRuleType());
        rule.setDescription(dto.getDescription().trim());
        rule.setLegalBasis(dto.getLegalBasis().trim());
        rule.setParamsJson(toJson(params));
        rule.setActive(true);
        rule.setCreatedByUserId(userId);
        return InformationRequestViews.rule(ruleRepository.save(rule));
    }

    @Override
    public AlertRuleView deactivateRule(Long ruleId) {
        ComplianceAlertRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Regla no encontrada: " + ruleId));
        rule.setActive(false);
        return InformationRequestViews.rule(ruleRepository.save(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AlertView> listAlerts(boolean pendingOnly, Pageable pageable) {
        Page<ComplianceAlert> page = pendingOnly
                ? alertRepository.findByReviewedAtIsNullOrderByDetectedAtDesc(pageable)
                : alertRepository.findAllByOrderByDetectedAtDesc(pageable);
        return PagedResponse.from(page.map(a -> InformationRequestViews.alert(a,
                userIdResolver.toPublicId(a.getCommercial().getId()))));
    }

    @Override
    public AlertView reviewAlert(Long alertId, Long reviewerUserId, AlertReviewRequest dto) {
        ComplianceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Alerta no encontrada: " + alertId));
        if (alert.getReviewedAt() != null) {
            throw new InvalidRequestException("La alerta ya fue revisada.");
        }
        alert.setReviewedAt(ZonedDateTime.now());
        alert.setReviewedByUserId(reviewerUserId);
        alert.setReviewNotes(dto.getNotes().trim());
        alertRepository.save(alert);
        return InformationRequestViews.alert(alert, userIdResolver.toPublicId(alert.getCommercial().getId()));
    }

    @Override
    public void raiseEventAlert(Long commercialId, InformationRequestTrigger trigger, String message, String dataUsedJson) {
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));
        ComplianceAlert alert = new ComplianceAlert();
        alert.setTrigger(trigger);
        alert.setCommercial(commercial);
        alert.setDetectedAt(ZonedDateTime.now());
        alert.setDataUsedJson(dataUsedJson);
        alert.setMessage(message);
        alertRepository.save(alert);
    }

    /**
     * Sin transacción envolvente: cada alerta se guarda por su cuenta, así un comercial que
     * falla no revierte las alertas de los demás.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int evaluateActiveRules() {
        List<ComplianceAlertRule> rules = ruleRepository.findByActiveTrue();
        if (rules.isEmpty()) {
            return 0;
        }
        List<CommercialOnboarding> active = onboardingRepository.findByCurrentStep(OnboardingStep.COMPLETED);
        int raised = 0;
        for (ComplianceAlertRule rule : rules) {
            for (CommercialOnboarding onboarding : active) {
                CommercialDetails commercial = onboarding.getCommercialDetails();
                try {
                    if (evaluateSalesGrowth(rule, commercial)) {
                        raised++;
                    }
                } catch (Exception e) {
                    log.warn("Regla {} v{} no se pudo evaluar para un comercial: {}",
                            rule.getCode(), rule.getVersion(), e.getMessage());
                }
            }
        }
        return raised;
    }

    /**
     * Compara las ventas en la plataforma de la ventana actual contra la anterior. Si la
     * anterior es cero no hay base de comparación: no se alerta con información incompleta.
     */
    boolean evaluateSalesGrowth(ComplianceAlertRule rule, CommercialDetails commercial) {
        if (alertRepository.existsByRule_CodeAndCommercial_IdAndReviewedAtIsNull(rule.getCode(), commercial.getId())) {
            return false;
        }
        JsonNode params = readJson(rule.getParamsJson());
        int windowDays = params.path("windowDays").asInt();
        BigDecimal minGrowthPct = new BigDecimal(params.path("minGrowthPct").asText());
        long minCurrent = params.path("minCurrentSalesCents").asLong();

        ZonedDateTime now = ZonedDateTime.now(BOGOTA);
        ZonedDateTime currentStart = now.minusDays(windowDays);
        ZonedDateTime previousStart = currentStart.minusDays(windowDays);
        BigDecimal current = orZero(purchaseItemService.getTotalCommercialSalesAmountByDateRange(
                commercial.getId(), currentStart, now));
        BigDecimal previous = orZero(purchaseItemService.getTotalCommercialSalesAmountByDateRange(
                commercial.getId(), previousStart, currentStart));

        if (previous.signum() <= 0 || current.compareTo(BigDecimal.valueOf(minCurrent)) < 0) {
            return false;
        }
        BigDecimal growthPct = current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
        if (growthPct.compareTo(minGrowthPct) < 0) {
            return false;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleCode", rule.getCode());
        data.put("ruleVersion", rule.getVersion());
        data.put("windowDays", windowDays);
        data.put("currentWindowStart", currentStart.toString());
        data.put("currentSalesCents", current);
        data.put("previousSalesCents", previous);
        data.put("growthPct", growthPct);

        ComplianceAlert alert = new ComplianceAlert();
        alert.setRule(rule);
        alert.setTrigger(InformationRequestTrigger.MATERIAL_OPERATION_INCREASE);
        alert.setCommercial(commercial);
        alert.setDetectedAt(ZonedDateTime.now());
        alert.setDataUsedJson(toJson(data));
        alert.setMessage("Las ventas registradas en VERYGANA en los últimos " + windowDays + " días ("
                + pesos(current) + ") superan en " + growthPct.toPlainString() + " % las de los " + windowDays
                + " días anteriores (" + pesos(previous) + "), según la regla " + rule.getCode() + " v"
                + rule.getVersion() + ". " + SCOPE_NOTICE);
        alertRepository.save(alert);
        return true;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String pesos(BigDecimal cents) {
        return "$" + cents.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).toPlainString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar a JSON", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parámetros de regla inválidos", e);
        }
    }
}
