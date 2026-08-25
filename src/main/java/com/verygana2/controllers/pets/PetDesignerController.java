package com.verygana2.controllers.pets;

import com.verygana2.dtos.pet.AddCatalogRequestCommentDTO;
import com.verygana2.dtos.pet.CatalogRequestCommentDTO;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetAssetUploadRequestDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.dtos.pet.CatalogRequestRejectionDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneCanvasDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;
import com.verygana2.services.interfaces.pet.PetAssetService;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import com.verygana2.services.interfaces.pet.PetSceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/game-designer/pet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GAME_DESIGNER')")
public class PetDesignerController {

    private final PetCatalogService catalogService;
    private final PetSceneService sceneService;
    private final PetNotificationService notificationService;
    private final CatalogIntegrationRequestService integrationRequestService;
    private final PetAssetService assetService;

    // ── ASSETS ────────────────────────────────────────────────────────────────

    /**
     * Paso 1 para cualquier imagen o video que el diseñador necesite subir.
     *
     * Devuelve una URL pre-firmada y la {@code objectKey} que hay que usar después:
     *
     * <pre>
     *   POST /game-designer/pet/assets   { kind, contentType, originalFileName, sizeBytes }
     *        → { objectKey, uploadUrl, expiresInSeconds }
     *   PUT  &lt;uploadUrl&gt;  (body: el archivo, con el Content-Type declarado)
     *
     *   kind = CATALOG_SPRITE → mandar objectKey como spriteObjectKey del ítem
     *   kind = SCENE_OBJECT   → mandar objectKey como objectKey del objeto de escena
     * </pre>
     */
    @PostMapping("/assets")
    public ResponseEntity<PetImageUploadPermissionDTO> prepareAssetUpload(
            @Valid @RequestBody PetAssetUploadRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(assetService.prepareUpload(userId, dto));
    }

    // ── CATÁLOGO ──────────────────────────────────────────────────────────────

    @GetMapping("/catalog")
    public ResponseEntity<List<PetCatalogItemResponseDTO>> getAllCatalogItems() {
        return ResponseEntity.ok(catalogService.getAllCatalogItemsAdmin());
    }

    @PostMapping("/catalog")
    public ResponseEntity<PetCatalogItemResponseDTO> createCatalogItem(
            @RequestBody PetCatalogItemRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createCatalogItem(dto));
    }




    @PutMapping("/catalog/{id}")
    public ResponseEntity<PetCatalogItemResponseDTO> updateCatalogItem(
            @PathVariable Long id,
            @RequestBody PetCatalogItemRequestDTO dto) {
        return ResponseEntity.ok(catalogService.updateCatalogItem(id, dto));
    }

    @DeleteMapping("/catalog/{id}")
    public ResponseEntity<Void> deleteCatalogItem(@PathVariable Long id) {
        catalogService.deleteCatalogItem(id);
        return ResponseEntity.noContent().build();
    }

    // ── ESCENAS ───────────────────────────────────────────────────────────────

    @GetMapping("/scenes")
    public ResponseEntity<List<PetSceneAdminResponseDTO>> getAllScenes() {
        return ResponseEntity.ok(sceneService.getAllScenesAdmin());
    }

    /**
     * Área de juego contra la que el editor dibuja el lienzo de referencia.
     *
     * Va aparte del listado de escenas porque no depende de ninguna: es una
     * propiedad del build. El editor la pide una vez y, si falla, cae a su propio
     * defecto — quedarse sin lienzo por esto sería peor que dibujarlo aproximado.
     */
    @GetMapping("/scenes/canvas")
    public ResponseEntity<PetSceneCanvasDTO> getSceneCanvas() {
        return ResponseEntity.ok(sceneService.getSceneCanvas());
    }

    @PostMapping("/scenes")
    public ResponseEntity<PetSceneAdminResponseDTO> createScene(
            @RequestBody PetSceneRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sceneService.createScene(dto));
    }

    @PutMapping("/scenes/{id}")
    public ResponseEntity<PetSceneAdminResponseDTO> updateScene(
            @PathVariable Long id,
            @RequestBody PetSceneRequestDTO dto) {
        return ResponseEntity.ok(sceneService.updateScene(id, dto));
    }

    @DeleteMapping("/scenes/{id}")
    public ResponseEntity<Void> deleteScene(@PathVariable Long id) {
        sceneService.deleteScene(id);
        return ResponseEntity.noContent().build();
    }

    // ── NOTIFICACIONES ────────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<List<PetNotificationResponseDTO>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotificationsAdmin());
    }

    @PostMapping("/notifications")
    public ResponseEntity<PetNotificationResponseDTO> createNotification(
            @RequestBody PetNotificationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(dto));
    }

    @PutMapping("/notifications/{id}")
    public ResponseEntity<PetNotificationResponseDTO> updateNotification(
            @PathVariable Long id,
            @RequestBody PetNotificationRequestDTO dto) {
        return ResponseEntity.ok(notificationService.updateNotification(id, dto));
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    // ── SOLICITUDES ASIGNADAS A ESTE DISEÑADOR ────────────────────────────────
    // Igual que en branding: aprobar/rechazar y asignar son del admin
    // (/api/admin/pet-requests). Aquí el diseñador solo ve y trabaja lo suyo.

    /** Bandeja del diseñador: solo las solicitudes que el admin le asignó. */
    @GetMapping("/requests")
    public ResponseEntity<List<CatalogIntegrationResponseDTO>> getMyAssignedRequests(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(integrationRequestService.getAssignedRequests(userId));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<CatalogIntegrationResponseDTO> getAssignedRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(integrationRequestService.getAssignedRequestDetail(id, userId));
    }

    /** Guardado parcial del ítem en construcción. Las claves son las de PetCatalogItemRequestDTO. */
    @PatchMapping("/requests/{id}/draft")
    public ResponseEntity<CatalogIntegrationResponseDTO> saveItemDraft(
            @PathVariable Long id,
            @RequestBody Map<String, Object> draft,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(integrationRequestService.saveItemDraft(id, userId, draft));
    }

    /** Publica el borrador como ítem real del catálogo y cierra la solicitud. */
    @PostMapping("/requests/{id}/publish")
    public ResponseEntity<CatalogIntegrationResponseDTO> publishCatalogItem(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(integrationRequestService.publishCatalogItem(id, userId));
    }

    // ── Hilo con el comercial ─────────────────────────────────────────────

    @GetMapping("/requests/{id}/comments")
    public ResponseEntity<List<CatalogRequestCommentDTO>> getComments(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(
                integrationRequestService.getComments(id, userId, CommentAuthorRole.DESIGNER));
    }

    @PostMapping("/requests/{id}/comments")
    public ResponseEntity<CatalogRequestCommentDTO> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCatalogRequestCommentDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                integrationRequestService.addComment(id, userId, CommentAuthorRole.DESIGNER, dto.content()));
    }
}