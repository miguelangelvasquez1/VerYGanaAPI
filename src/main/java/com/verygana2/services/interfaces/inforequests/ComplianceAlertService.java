package com.verygana2.services.interfaces.inforequests;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertReviewRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertView;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;

/**
 * Alertas para compliance (4.7 / 4.8): informativas, explicables y trazables. Ninguna cambia
 * responsabilidades, acredita incumplimiento ni aplica medidas.
 */
public interface ComplianceAlertService {

    List<AlertRuleView> listRules();

    /** Crea la regla, o una versión nueva si el código ya existe (la anterior se desactiva). */
    AlertRuleView createRule(AlertRuleRequest request, Long userId);

    AlertRuleView deactivateRule(Long ruleId);

    PagedResponse<AlertView> listAlerts(boolean pendingOnly, Pageable pageable);

    AlertView reviewAlert(Long alertId, Long reviewerUserId, AlertReviewRequest request);

    /** Alerta originada por un evento del expediente, sin regla (p. ej. cambio de actividades). */
    void raiseEventAlert(Long commercialId, InformationRequestTrigger trigger, String message, String dataUsedJson);

    /** Evalúa las reglas activas. Sin reglas activas no hace nada: no hay umbrales por defecto. */
    int evaluateActiveRules();
}
