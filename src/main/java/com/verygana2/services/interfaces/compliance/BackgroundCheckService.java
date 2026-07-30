package com.verygana2.services.interfaces.compliance;

import java.util.List;
import java.util.Map;

import com.verygana2.models.compliance.BackgroundCheck;

public interface BackgroundCheckService {

    /**
     * Solicita a ZapSign una consulta de antecedentes del representante legal y, si el
     * comercio es persona jurídica, también de la empresa (NIT). Cada solicitud crea checks
     * nuevos en ZapSign (force_creation=true): es una acción deliberada de compliance, no
     * automática, y cada check tiene costo en créditos de ZapSign.
     */
    List<BackgroundCheck> requestChecks(Long contractId, Long officerId);

    /** Historial de checks solicitados para un contrato, más reciente primero. */
    List<BackgroundCheck> listByContract(Long contractId);

    /** Reconsulta el estado en ZapSign y actualiza el registro local (para cuando el webhook no ha llegado aún). */
    BackgroundCheck refreshStatus(Long backgroundCheckId);

    /** Hallazgos detallados de un check ya completado, obtenidos en vivo desde ZapSign. */
    Map<String, Object> getDetail(Long backgroundCheckId);

    /** Invocado por el webhook de ZapSign cuando un check termina (background_check_completed). */
    void handleWebhookCompleted(String checkId);
}
