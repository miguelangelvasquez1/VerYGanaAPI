package com.verygana2.services.interfaces.inforequests;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CancelRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CreateRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RespondRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ReviewRequest;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.inforequests.InformationRequest;

/**
 * Requerimientos de actualización y aclaración al empresario (4.7 / 4.8). Pedir información
 * nunca suspende ni sanciona; el cierre sale solo de una revisión.
 */
public interface InformationRequestService {

    /** Requerimiento que abre el sistema por un evento o una regla. */
    record SystemRequest(Long commercialId, InformationRequestTrigger trigger, String ruleCode, Integer ruleVersion,
            String reason, String relationToOperation, String dedupKey, List<ItemRequest> items) {
    }

    /**
     * Abre el requerimiento, o devuelve el que ya esté abierto con la misma clave (reintentos
     * o eventos repetidos no lo duplican). Lo comunica al empresario por la plataforma.
     */
    InformationRequest openSystemRequest(SystemRequest request);

    /** True si alguna vez se abrió un requerimiento con esta clave (abierto o cerrado). */
    boolean existsByDedupKey(String dedupKey);

    // ---- Empresario ----

    List<RequestView> listForCommercial(Long commercialId);

    RequestView getForCommercial(Long commercialId, UUID requestPublicId);

    RequestView respond(Long commercialId, UUID requestPublicId, RespondRequest request);

    // ---- Compliance ----

    PagedResponse<RequestView> search(InformationRequestStatus status, UUID commercialPublicId, Pageable pageable);

    RequestView getForCompliance(UUID requestPublicId);

    RequestView create(CreateRequest request, Long issuerUserId);

    RequestView review(UUID requestPublicId, Long reviewerUserId, ReviewRequest request);

    RequestView cancel(UUID requestPublicId, Long userId, CancelRequest request);
}
