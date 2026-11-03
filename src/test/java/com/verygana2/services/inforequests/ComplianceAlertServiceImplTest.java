package com.verygana2.services.inforequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleView;
import com.verygana2.models.enums.inforequests.AlertRuleType;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.inforequests.ComplianceAlert;
import com.verygana2.models.inforequests.ComplianceAlertRule;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRuleRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

/**
 * Alertas (4.8): sin reglas no hay detección; con regla, la alerta explica su alcance y no
 * se presenta como evaluación completa de obligaciones.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceAlertServiceImpl")
class ComplianceAlertServiceImplTest {

    @Mock private ComplianceAlertRuleRepository ruleRepository;
    @Mock private ComplianceAlertRepository alertRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private PurchaseItemService purchaseItemService;
    @Mock private UserIdResolver userIdResolver;

    private ComplianceAlertServiceImpl service;
    private CommercialDetails commercial;
    private ComplianceAlertRule rule;

    @BeforeEach
    void setUp() {
        service = new ComplianceAlertServiceImpl(ruleRepository, alertRepository, commercialDetailsRepository,
                onboardingRepository, purchaseItemService, userIdResolver, new ObjectMapper());
        commercial = new CommercialDetails();
        commercial.setId(7L);
        rule = new ComplianceAlertRule();
        rule.setCode("SALES_GROWTH");
        rule.setVersion(2);
        rule.setRuleType(AlertRuleType.PLATFORM_SALES_GROWTH);
        rule.setParamsJson("{\"windowDays\":30,\"minGrowthPct\":50,\"minCurrentSalesCents\":100000}");
        lenient().when(alertRepository.existsByRule_CodeAndCommercial_IdAndReviewedAtIsNull("SALES_GROWTH", 7L))
                .thenReturn(false);
    }

    private void sales(long previous, long current) {
        // La primera llamada es la ventana actual; la segunda, la anterior.
        when(purchaseItemService.getTotalCommercialSalesAmountByDateRange(eq(7L), any(), any()))
                .thenReturn(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    @Test
    @DisplayName("sin reglas activas no se evalúa nada: no hay umbrales por defecto")
    void noRulesNoAlerts() {
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of());

        assertThat(service.evaluateActiveRules()).isZero();
        verify(onboardingRepository, never()).findByCurrentStep(any());
    }

    @Test
    @DisplayName("un crecimiento sobre el umbral configurado genera una alerta con datos, versión y alcance")
    void growthAboveThreshold() {
        sales(1_000_000, 2_000_000);

        assertThat(service.evaluateSalesGrowth(rule, commercial)).isTrue();

        ArgumentCaptor<ComplianceAlert> alert = ArgumentCaptor.forClass(ComplianceAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getTrigger()).isEqualTo(InformationRequestTrigger.MATERIAL_OPERATION_INCREASE);
        assertThat(alert.getValue().getMessage()).contains("SALES_GROWTH v2").contains("Solo incluye operaciones en VERYGANA");
        assertThat(alert.getValue().getDataUsedJson()).contains("\"growthPct\":100.00");
    }

    @Test
    @DisplayName("un crecimiento bajo el umbral no genera alerta")
    void growthBelowThreshold() {
        sales(1_000_000, 1_200_000);

        assertThat(service.evaluateSalesGrowth(rule, commercial)).isFalse();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin ventas en la ventana anterior no hay base: no se alerta con información incompleta")
    void noBaselineNoAlert() {
        sales(0, 5_000_000);

        assertThat(service.evaluateSalesGrowth(rule, commercial)).isFalse();
    }

    @Test
    @DisplayName("con una alerta sin revisar de la misma regla no se repite cada día")
    void pendingAlertIsNotRepeated() {
        when(alertRepository.existsByRule_CodeAndCommercial_IdAndReviewedAtIsNull("SALES_GROWTH", 7L)).thenReturn(true);

        assertThat(service.evaluateSalesGrowth(rule, commercial)).isFalse();
        verify(purchaseItemService, never()).getTotalCommercialSalesAmountByDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("cambiar una regla crea una versión nueva y desactiva la anterior")
    void newVersionDeactivatesPrevious() {
        rule.setActive(true);
        when(ruleRepository.findFirstByCodeOrderByVersionDesc("SALES_GROWTH")).thenReturn(Optional.of(rule));
        when(ruleRepository.findAllByOrderByCodeAscVersionDesc()).thenReturn(List.of(rule));
        when(ruleRepository.save(any(ComplianceAlertRule.class))).thenAnswer(inv -> inv.getArgument(0));
        AlertRuleRequest dto = new AlertRuleRequest();
        dto.setCode("SALES_GROWTH");
        dto.setRuleType(AlertRuleType.PLATFORM_SALES_GROWTH);
        dto.setDescription("Crecimiento de ventas");
        dto.setLegalBasis("Política interna aprobada");
        dto.setWindowDays(30);
        dto.setMinGrowthPct(new BigDecimal("80"));
        dto.setMinCurrentSalesCents(0L);

        AlertRuleView created = service.createRule(dto, 99L);

        assertThat(created.version()).isEqualTo(3);
        assertThat(created.active()).isTrue();
        assertThat(rule.isActive()).isFalse();
    }
}
