package com.verygana2.controllers;



import com.verygana2.dtos.pet.*;
import com.verygana2.services.interfaces.*;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import com.verygana2.services.interfaces.pet.PetSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pet")
public class PetGameConfigController {

    private final PetCatalogService petCatalogService;
    private final PetSceneService petSceneService;
    private final PetNotificationService petNotificationService;

    // ── Endpoints públicos que consume el juego ───────────

    @GetMapping("/scenes")
    public ResponseEntity<Map<String, List<PetSceneResponseDTO>>> getScenes() {
        return ResponseEntity.ok(Map.of("scenes", petSceneService.getAllScenes()));
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, List<PetCatalogItemResponseDTO>>> getCatalog() {
        return ResponseEntity.ok(Map.of("foods", petCatalogService.getAllCatalogItems()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, List<PetNotificationResponseDTO>>> getNotifications() {
        return ResponseEntity.ok(Map.of("notifications", petNotificationService.getAllNotifications()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(@PathVariable String id) {
        petNotificationService.markNotificationAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

}
