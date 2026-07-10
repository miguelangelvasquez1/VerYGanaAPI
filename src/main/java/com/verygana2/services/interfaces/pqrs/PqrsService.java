package com.verygana2.services.interfaces.pqrs;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;

public interface PqrsService {

    PqrsResponseDTO createPqrs(CreatePqrsRequestDTO dto, Long requesterUserId);

    PagedResponse<PqrsResponseDTO> getMyPqrs(Long requesterUserId, Pageable pageable);

    PqrsResponseDTO getMyPqrsDetail(Long pqrsId, Long requesterUserId);

    PagedResponse<PqrsAdminDetailDTO> getAssignedPqrs(Long adminUserId, PqrsStatus status, PqrsType type, Pageable pageable);

    PqrsAdminDetailDTO getPqrsDetailForAdmin(Long pqrsId, Long adminUserId);

    void markUnderReview(Long pqrsId, Long adminUserId);

    void respondToPqrs(Long pqrsId, RespondPqrsRequestDTO dto, Long adminUserId);

    /**
     * Reintenta asignar un admin a los PQRS que quedaron PENDIENTE_ASIGNACION
     * (no había ningún admin activo en el momento de la creación).
     */
    void retryPendingAssignments();

    /**
     * Alerta a los admins con PQRS por vencer o ya vencidos dentro del plazo legal.
     */
    void sendSlaAlerts(int daysBeforeDueDateToAlert);
}
