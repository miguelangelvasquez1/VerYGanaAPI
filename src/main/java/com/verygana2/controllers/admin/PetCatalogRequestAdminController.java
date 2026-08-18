package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.pet.AddCatalogRequestCommentDTO;
import com.verygana2.dtos.pet.ApprovePetRequestDTO;
import com.verygana2.dtos.pet.CatalogRequestCommentDTO;
import com.verygana2.dtos.pet.AssignPetDesignerDTO;
import com.verygana2.dtos.pet.CatalogRequestRejectionDTO;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bandeja del admin para las solicitudes de integración al catálogo de mascotas.
 *
 * Espejo de {@link BrandingRequestAdminController}: el admin revisa lo que envían
 * los comerciales, aprueba asignando un diseñador y puede reasignarlo después.
 * Hasta que no hay asignación, ningún diseñador ve la solicitud.
 */
@RestController
@RequestMapping("/api/admin/pet-requests")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class PetCatalogRequestAdminController {

    private final CatalogIntegrationRequestService requestService;

    /** Lista todas las solicitudes, opcionalmente filtradas por estado. */
    @GetMapping
    public ResponseEntity<List<CatalogIntegrationResponseDTO>> getAll(
            @RequestParam(required = false) CatalogRequestStatus status) {
        if (status != null) {
            return ResponseEntity.ok(requestService.getRequestsByStatus(status));
        }
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    /** Lista diseñadores activos — para el selector al aprobar o reasignar. */
    @GetMapping("/designers")
    public ResponseEntity<List<GameDesignerSummaryDTO>> getActiveDesigners() {
        return ResponseEntity.ok(requestService.getActiveDesigners());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogIntegrationResponseDTO> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.getRequestDetail(id));
    }

    /** Marca que el admin está revisando (PENDING → IN_REVIEW). */
    @PatchMapping("/{id}/review")
    public ResponseEntity<CatalogIntegrationResponseDTO> markInReview(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.markInReview(id));
    }

    /** Aprueba y asigna el diseñador que armará el ítem. */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<CatalogIntegrationResponseDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApprovePetRequestDTO dto) {
        return ResponseEntity.ok(requestService.approve(id, dto));
    }

    /** Reasigna el diseñador de una solicitud ya aprobada. */
    @PatchMapping("/{id}/assign-designer")
    public ResponseEntity<CatalogIntegrationResponseDTO> assignDesigner(
            @PathVariable Long id,
            @Valid @RequestBody AssignPetDesignerDTO dto) {
        return ResponseEntity.ok(requestService.assignDesigner(id, dto.designerUserId()));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<CatalogIntegrationResponseDTO> reject(
            @PathVariable Long id,
            @Valid @RequestBody CatalogRequestRejectionDTO dto) {
        return ResponseEntity.ok(requestService.reject(id, dto.reason()));
    }

    // ── Hilo con el comercial y el diseñador ──────────────────────────────

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CatalogRequestCommentDTO>> getComments(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(
                requestService.getComments(id, userId, CommentAuthorRole.ADMIN));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CatalogRequestCommentDTO> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCatalogRequestCommentDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                requestService.addComment(id, userId, CommentAuthorRole.ADMIN, dto.content()));
    }
}
