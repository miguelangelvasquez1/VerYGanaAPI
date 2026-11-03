package com.verygana2.schedulers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemRequest;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.enums.inforequests.RequestItemType;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.services.interfaces.inforequests.ComplianceAlertService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService.SystemRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detección diaria del expediente (4.8):
 * <ul>
 *   <li>Documentos vencidos: solo los que tienen una fecha de vencimiento comprobada en la
 *       revisión. Abre un requerimiento por documento, una sola vez.</li>
 *   <li>Reglas de alerta activas (p. ej. crecimiento de ventas). Sin reglas, no hace nada.</li>
 * </ul>
 * Detectar nunca suspende ni sanciona.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpedienteReviewScheduler {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CommercialDocumentRepository documentRepository;
    private final InformationRequestService requestService;
    private final ComplianceAlertService alertService;

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Bogota")
    public void detectExpiredDocuments() {
        LocalDate today = LocalDate.now(BOGOTA);
        List<CommercialDocument> expired = documentRepository
                .findByStatusAndExpiresAtBeforeAndCommercialIsNotNull(CommercialDocumentStatus.VALIDATED, today);
        int opened = 0;
        for (CommercialDocument document : expired) {
            String dedupKey = "DOCUMENT_EXPIRED:" + document.getId();
            // Una vez por documento: si se canceló o se cerró, no se reabre cada día.
            if (requestService.existsByDedupKey(dedupKey)) {
                continue;
            }
            try {
                ItemRequest item = new ItemRequest();
                item.setItemType(RequestItemType.DOCUMENT);
                item.setDocumentType(document.getDocumentType());
                item.setDescription("Cargue una versión vigente de " + document.getDocumentType()
                        + " como reemplazo del documento " + document.getId() + ".");
                item.setAcceptableResponse("El mismo documento, del mismo titular, con vigencia actual.");
                requestService.openSystemRequest(new SystemRequest(document.getCommercial().getId(),
                        InformationRequestTrigger.DOCUMENT_EXPIRED, "DOCUMENT_EXPIRY", 1,
                        "El documento " + document.getDocumentType() + " venció el " + document.getExpiresAt().format(DATE) + ".",
                        "Numeral 4.8: un documento pertinente vencido debe actualizarse para mantener vigente la "
                                + "información de la vinculación.",
                        dedupKey, List.of(item)));
                opened++;
            } catch (Exception e) {
                log.warn("No se pudo abrir el requerimiento del documento vencido {}: {}", document.getId(), e.getMessage());
            }
        }
        if (opened > 0) {
            log.info("[EXPEDIENTE] {} requerimientos por documentos vencidos", opened);
        }
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "America/Bogota")
    public void evaluateAlertRules() {
        int raised = alertService.evaluateActiveRules();
        if (raised > 0) {
            log.info("[EXPEDIENTE] {} alertas nuevas por reglas activas", raised);
        }
    }
}
