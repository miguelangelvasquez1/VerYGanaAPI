package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.pet.ApprovePetRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogRequestCommentDTO;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.dtos.pet.PetImageUploadRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;

import java.util.List;
import java.util.Map;

/**
 * Solicitudes de integración al catálogo de mascotas.
 *
 * El flujo replica el de branding de juegos: el comercial envía, el <b>admin</b>
 * revisa y aprueba asignando un diseñador, y solo entonces ese diseñador la ve en
 * su bandeja y arma el ítem.
 */
public interface CatalogIntegrationRequestService {

    // ── Comercial ─────────────────────────────────────────────────────────────
    PetImageUploadPermissionDTO prepareImageUpload(Long userId, PetImageUploadRequestDTO dto);
    CatalogIntegrationResponseDTO submit(Long userId, CatalogIntegrationRequestDTO dto);
    List<CatalogIntegrationResponseDTO> getMyRequests(Long userId);

    // ── Admin ─────────────────────────────────────────────────────────────────
    List<CatalogIntegrationResponseDTO> getAllRequests();
    List<CatalogIntegrationResponseDTO> getRequestsByStatus(CatalogRequestStatus status);
    CatalogIntegrationResponseDTO getRequestDetail(Long requestId);
    List<GameDesignerSummaryDTO> getActiveDesigners();
    CatalogIntegrationResponseDTO markInReview(Long requestId);

    /** Aprueba y asigna el diseñador que armará el ítem (PENDING/IN_REVIEW → APPROVED). */
    CatalogIntegrationResponseDTO approve(Long requestId, ApprovePetRequestDTO dto);

    /** Reasigna el diseñador de una solicitud ya aprobada. */
    CatalogIntegrationResponseDTO assignDesigner(Long requestId, Long designerUserId);

    CatalogIntegrationResponseDTO reject(Long requestId, String reason);

    // ── Diseñador (solo lo asignado a él) ─────────────────────────────────────
    List<CatalogIntegrationResponseDTO> getAssignedRequests(Long designerUserId);
    CatalogIntegrationResponseDTO getAssignedRequestDetail(Long requestId, Long designerUserId);

    /** Guarda (parcialmente) el borrador del ítem. */
    CatalogIntegrationResponseDTO saveItemDraft(Long requestId, Long designerUserId, Map<String, Object> draft);

    /** Convierte el borrador en un PetCatalogItem real y cierra la solicitud. */
    CatalogIntegrationResponseDTO publishCatalogItem(Long requestId, Long designerUserId);

    // ── Hilo de conversación (comercial ↔ diseñador ↔ admin) ──────────────
    // El rol define qué se valida: el comercial solo entra a sus solicitudes, el
    // diseñador a las que tiene asignadas, el admin a todas.
    List<CatalogRequestCommentDTO> getComments(Long requestId, Long userId, CommentAuthorRole role);
    CatalogRequestCommentDTO addComment(Long requestId, Long authorUserId,
                                        CommentAuthorRole role, String content);

    /**
     * Métricas de venta de los productos que el comercial publicó en el juego.
     * Requiere plan con CAN_HAVE_PETS, igual que crear la solicitud.
     *
     * @param userId id del usuario comercial autenticado
     */
    java.util.List<com.verygana2.dtos.pet.PetProductMetricsDTO> getMyProductMetrics(
            Long userId, java.time.LocalDate from, java.time.LocalDate to);

    /**
     * Ventas por día para la gráfica de evolución, con los días sin ventas rellenados
     * en cero. Mismo requisito de plan que {@link #getMyProductMetrics}.
     */
    java.util.List<com.verygana2.dtos.pet.PetSalesPointDTO> getMyDailySales(
            Long userId, java.time.LocalDate from, java.time.LocalDate to);
}
