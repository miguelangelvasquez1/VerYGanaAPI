package com.verygana2.services.interfaces.pqrs;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.PurchaseItem;

public interface PqrsService {

    PqrsResponseDTO createPqrs(CreatePqrsRequestDTO dto, Long requesterUserId);

    /**
     * Radica un PQRS ya vinculado a un PurchaseItem (ver
     * /purchaseItems/{id}/report). Siempre type=RECLAMO. Mientras este PQRS
     * no se resuelva, el ítem queda excluido del payout diario (ver
     * PurchaseItemRepository.findClaimedWithoutPayout).
     *
     * @param assetIds opcional — evidencia (PqrsAsset) ya confirmada que el
     *                  comprador quiere adjuntar.
     */
    PqrsResponseDTO createPqrsForPurchaseItem(PurchaseItem item, MarketplaceIssueReason reason,
            String description, Long requesterUserId, List<Long> assetIds);

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
