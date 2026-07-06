package com.verygana2.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.AddCommentDTO;
import com.verygana2.dtos.branding.BrandingGameDTO;
import com.verygana2.dtos.branding.BrandingRequestCommentDTO;
import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.ConfirmCorporateResourceDTO;
import com.verygana2.dtos.branding.CorporateResourceUploadPermissionDTO;
import com.verygana2.dtos.branding.CreateBrandingRequestDTO;
import com.verygana2.dtos.branding.SubmitForReviewDTO;
import com.verygana2.dtos.branding.UpdateBrandingRequestConfigDTO;
import com.verygana2.models.enums.CampaignGoal;
import com.verygana2.services.interfaces.BrandingRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/branding-requests")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class BrandingRequestController {

    private final BrandingRequestService brandingRequestService;

    /**
     * GET /branding-requests/campaign-goals — valores del enum CampaignGoal para mostrar en el paso "Configuración".
     */
    @GetMapping("/campaign-goals")
    public ResponseEntity<CampaignGoal[]> getCampaignGoals() {
        return ResponseEntity.ok(CampaignGoal.values());
    }

    /**
     * Catálogo de juegos disponibles para branding — el anunciante lo consulta
     * ANTES de crear la solicitud para escoger el juego.
     * GET /branding-requests/games
     */
    @GetMapping("/games")
    public ResponseEntity<Page<BrandingGameDTO>> getGamesForBranding(
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(brandingRequestService.getGamesForBranding(pageable));
    }

    /**
     * Paso 1: Anunciante crea una solicitud de branding (queda en DRAFT).
     * POST /branding-requests
     */
    @PostMapping
    public ResponseEntity<BrandingRequestSummaryDTO> createBrandingRequest(
            @Valid @RequestBody CreateBrandingRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        BrandingRequestSummaryDTO response = brandingRequestService.createBrandingRequest(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Paso 2: Anunciante envía la solicitud al admin para revisión (DRAFT → PENDING_REVIEW).
     * El campo `notes` es opcional: si se envía, queda como comentario en el hilo.
     * POST /branding-requests/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submitForReview(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SubmitForReviewDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        brandingRequestService.submitForReview(id, userId, dto != null ? dto.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    /**
     * Opcional (puede hacerse en cualquier momento antes de READY_TO_LAUNCH):
     * Actualiza targeting (categorías, género, edad, municipios) y configuración de
     * recompensas (completionRewardCents, maxRewardPerSessionCents, etc.).
     * PATCH /branding-requests/{id}/config
     */
    @PatchMapping("/{id}/config")
    public ResponseEntity<Void> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandingRequestConfigDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        brandingRequestService.updateConfig(id, dto, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /branding-requests/{id} — detalle completo de una solicitud (para vista de edición/detalle).
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrandingRequestDetailDTO> getBrandingRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(brandingRequestService.getBrandingRequestDetail(id, userId));
    }

    /**
     * GET /branding-requests — lista las solicitudes del anunciante autenticado.
     */
    @GetMapping
    public ResponseEntity<List<BrandingRequestSummaryDTO>> getMyBrandingRequests(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(brandingRequestService.getMyBrandingRequests(userId));
    }

    /**
     * POST /branding-requests/{id}/corporate-resources/upload-url
     * Anunciante solicita URL presignada para subir un recurso corporativo (logo, imagen, etc.).
     */
    @PostMapping("/{id}/corporate-resources/upload-url")
    public ResponseEntity<CorporateResourceUploadPermissionDTO> generateResourceUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody FileUploadRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(brandingRequestService.generateResourceUploadUrl(id, dto, userId));
    }

    /**
     * POST /branding-requests/{id}/corporate-resources/confirm
     * Anunciante confirma que el recurso corporativo fue subido correctamente.
     */
    @PostMapping("/{id}/corporate-resources/confirm")
    public ResponseEntity<Void> confirmCorporateResource(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmCorporateResourceDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        brandingRequestService.confirmCorporateResource(id, dto, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /branding-requests/{id}/approve-design
     * El anunciante aprueba el diseño entregado por el diseñador → READY_TO_LAUNCH.
     */
    @PostMapping("/{id}/approve-design")
    public ResponseEntity<Void> approveDesign(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        brandingRequestService.approveDesign(id, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /branding-requests/{id}/preview-url
     * El anunciante obtiene la URL para previsualizar el juego diseñado.
     * Solo disponible cuando el diseñador ya entregó (gameConfig no vacío).
     */
    @GetMapping("/{id}/preview-url")
    public ResponseEntity<Map<String, String>> getPreviewUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        String url = brandingRequestService.getPreviewUrl(id, userId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /branding-requests/{id}/request-design-changes
     * El anunciante solicita cambios al diseñador → CHANGES_REQUESTED.
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<BrandingRequestCommentDTO>> getComments(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(brandingRequestService.getComments(id, userId));
    }

    /**
     * POST /branding-requests/{id}/request-design-changes
     * El anunciante solicita cambios → CHANGES_REQUESTED. Las notas van por el chat (/comments).
     */
    @PostMapping("/{id}/request-design-changes")
    public ResponseEntity<Void> requestDesignChanges(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        brandingRequestService.requestDesignChanges(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<BrandingRequestCommentDTO> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(brandingRequestService.addCommentAsCommercial(id, userId, dto));
    }

}
