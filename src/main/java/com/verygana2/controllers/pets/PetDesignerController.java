package com.verygana2.controllers.pets;

import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.CatalogRequestRejectionDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import com.verygana2.services.interfaces.pet.PetSceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/game-designer/pet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
public class PetDesignerController {

    private final PetCatalogService catalogService;
    private final PetSceneService sceneService;
    private final PetNotificationService notificationService;
    private final CatalogIntegrationRequestService integrationRequestService;

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

    // ── SOLICITUDES DE INTEGRACIÓN DE COMERCIALES ─────────────────────────────

    @GetMapping("/requests")
    public ResponseEntity<List<CatalogIntegrationResponseDTO>> getAllRequests(
            @RequestParam(required = false) CatalogRequestStatus status) {
        if (status != null) {
            return ResponseEntity.ok(integrationRequestService.getRequestsByStatus(status));
        }
        return ResponseEntity.ok(integrationRequestService.getAllRequests());
    }

    @PatchMapping("/requests/{id}/review")
    public ResponseEntity<CatalogIntegrationResponseDTO> markInReview(@PathVariable Long id) {
        return ResponseEntity.ok(integrationRequestService.markInReview(id));
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<CatalogIntegrationResponseDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody PetCatalogItemRequestDTO catalogItemDto) {
        return ResponseEntity.ok(integrationRequestService.approve(id, catalogItemDto));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<CatalogIntegrationResponseDTO> reject(
            @PathVariable Long id,
            @Valid @RequestBody CatalogRequestRejectionDTO dto) {
        return ResponseEntity.ok(integrationRequestService.reject(id, dto.reason()));
    }
}