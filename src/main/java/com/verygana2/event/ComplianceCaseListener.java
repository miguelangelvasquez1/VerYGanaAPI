package com.verygana2.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemRequest;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.enums.inforequests.RequestItemType;
import com.verygana2.models.enums.tax.TaxProfileChangeType;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.services.interfaces.inforequests.ComplianceAlertService;
import com.verygana2.services.interfaces.inforequests.ComplianceCaseService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService.SystemRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abre la revisión que corresponde a cada evento del expediente, después del commit y en su
 * propia transacción: si falla, la aprobación del contrato o el envío de la ficha ya
 * quedaron guardados y no se revierten.
 *
 * <ul>
 *   <li>Casilla del compromiso sin marcar, negativa o falla técnica → requerimiento de
 *       aclaración al empresario (4.7). Nunca un rechazo automático.</li>
 *   <li>Cambio de actividades o establecimientos → alerta para compliance (4.8). El cambio
 *       lo hizo el propio empresario; compliance decide si hay que pedir algo.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceCaseListener {

    private static final String COMMITMENT_RELATION = "Numeral 4.7: compromiso de crecimiento y formalización, "
            + "apartado 9 de la Hoja Final de Aceptación Electrónica.";

    private final InformationRequestService requestService;
    private final ComplianceCaseService caseService;
    private final ComplianceAlertService alertService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFormalizationManifestation(FormalizationManifestationRecordedEvent event) {
        try {
            switch (event.getStatus()) {
                case MANIFESTED -> { /* evidencia completa: nada que aclarar */ }
                case NOT_MANIFESTED -> requestService.openSystemRequest(new SystemRequest(
                        event.getCommercialId(), InformationRequestTrigger.MISSING_MANIFESTATION, null, null,
                        "La casilla del compromiso de formalización no se recibió marcada al aprobar el contrato.",
                        COMMITMENT_RELATION + " La falta de manifestación da lugar a aclaración; no implica rechazo.",
                        "MISSING_MANIFESTATION:" + event.getManifestationId(),
                        List.of(clarification("Indique si desea manifestar el compromiso o explique por qué no lo hizo."))));
                case EXPRESS_REFUSAL -> requestService.openSystemRequest(new SystemRequest(
                        event.getCommercialId(), InformationRequestTrigger.EXPRESS_REFUSAL, null, null,
                        "Se registró una negativa expresa al compromiso de formalización.",
                        COMMITMENT_RELATION + " Antes de cualquier procedimiento se verifica el contenido y el "
                                + "fundamento de la negativa; no es una sanción automática.",
                        "EXPRESS_REFUSAL:" + event.getManifestationId(),
                        List.of(clarification("Explique el motivo de su negativa."))));
                case TECHNICAL_INCIDENT -> {
                    InformationRequest request = requestService.openSystemRequest(new SystemRequest(
                            event.getCommercialId(), InformationRequestTrigger.INCIDENT, null, null,
                            "La manifestación del compromiso de formalización no pudo registrarse por una falla técnica.",
                            COMMITMENT_RELATION + " Una falla técnica no es una negativa: se permite aclarar o completar.",
                            "MANIFESTATION_INCIDENT:" + event.getManifestationId(),
                            List.of(clarification("Indique si desea manifestar el compromiso de formalización."))));
                    caseService.recordSystemIncident(event.getCommercialId(), "CONTRACT_SHEET_SECTION_9",
                            "La casilla se envió sobre una versión del texto distinta a la vigente.",
                            event.getManifestationId(), request);
                }
            }
        } catch (Exception e) {
            log.error("No se pudo abrir la aclaración de la manifestación {}: {}",
                    event.getManifestationId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaxProfileChanged(TaxProfileChangedEvent event) {
        try {
            boolean activity = event.getChangeType() == TaxProfileChangeType.ACTIVITY_CHANGE;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("changeType", event.getChangeType().name());
            data.put("detail", event.getDetail());
            alertService.raiseEventAlert(event.getCommercialId(),
                    activity ? InformationRequestTrigger.ACTIVITY_CHANGE : InformationRequestTrigger.ESTABLISHMENT_CHANGE,
                    (activity ? "El empresario cambió sus actividades económicas" : "El empresario cambió sus establecimientos")
                            + " (" + event.getDetail() + "). Revise qué verificaciones requieren actualización"
                            + (activity ? ", incluida la evaluación de la Vocación Empresarial Principal" : "")
                            + ". La vocación no cambia automáticamente ni se recalculan operaciones históricas.",
                    objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.error("No se pudo registrar la alerta de cambio del expediente: {}", e.getMessage(), e);
        }
    }

    private static ItemRequest clarification(String description) {
        ItemRequest item = new ItemRequest();
        item.setItemType(RequestItemType.CLARIFICATION);
        item.setDescription(description);
        item.setAcceptableResponse("Una aclaración escrita.");
        return item;
    }
}
