package com.verygana2.services.interfaces.inforequests;

import java.util.List;
import java.util.UUID;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentView;
import com.verygana2.models.inforequests.InformationRequest;

/**
 * Hallazgos con medida contractual e incidencias técnicas (4.7 / 4.8). Un hallazgo es el
 * único camino por el que cambia el estado de la cuenta, y siempre con fundamento.
 */
public interface ComplianceCaseService {

    FindingView recordFinding(FindingRequest request, Long deciderUserId);

    List<FindingView> listFindings(UUID commercialPublicId);

    IncidentView recordIncident(IncidentRequest request, Long reporterUserId);

    /** Incidencia que registra el sistema (p. ej. la casilla llegó sobre otra versión del texto). */
    void recordSystemIncident(Long commercialId, String context, String description, Long manifestationId,
            InformationRequest request);

    List<IncidentView> listIncidents(UUID commercialPublicId);
}
