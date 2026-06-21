package com.verygana2.controllers;



import com.verygana2.dtos.pet.*;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.*;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import com.verygana2.services.interfaces.pet.PetSceneService;
import com.verygana2.services.interfaces.pet.PetSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pet")
public class PetGameConfigController {

    private final PetSessionService petSessionService;
    private final PetCatalogService petCatalogService;
    private final PetSceneService petSceneService;
    private final PetNotificationService petNotificationService;
    private final ConsumerDetailsRepository consumerDetailsRepository;

    // ── Iniciar sesión (requiere JWT) ─────────────────────
    @PostMapping("/session/init")
    public ResponseEntity<PetSessionResponseDTO> initSession(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long consumerId = getConsumerId(jwt);
        return ResponseEntity.ok(
                petSessionService.initSession(consumerId)
        );
    }


    @GetMapping("/catalog")
    public ResponseEntity<Map<String, List<PetCatalogItemResponseDTO>>> getCatalog(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        petSessionService.validateSession(body.sessionToken(), body.userHash());
        return ResponseEntity.ok(Map.of("foods", petCatalogService.getAllCatalogItems()));
    }

    @GetMapping("/scenes")
    public ResponseEntity<Map<String, List<PetSceneResponseDTO>>> getScenes(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        petSessionService.validateSession(body.sessionToken(), body.userHash());
        return ResponseEntity.ok(Map.of("scenes", petSceneService.getAllScenes()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, List<PetNotificationResponseDTO>>> getNotifications(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        petSessionService.validateSession(body.sessionToken(), body.userHash());
        return ResponseEntity.ok(Map.of("notifications", petNotificationService.getAllNotifications()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(
            @PathVariable String id,
            @RequestBody PetSessionRequestDTO body
    ) {
        petSessionService.validateSession(body.sessionToken(), body.userHash());
        petNotificationService.markNotificationAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Long getConsumerId(Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return consumerDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Consumer not found"))
                .getId();
    }
}
