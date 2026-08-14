package com.verygana2.controllers.pets;



import com.verygana2.dtos.pet.*;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import com.verygana2.services.interfaces.pet.PetSceneService;
import com.verygana2.services.interfaces.pet.PetSessionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('CONSUMER')")
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
        validateSession(body);
        return ResponseEntity.ok(Map.of("foods", petCatalogService.getAllCatalogItems()));
    }

    @GetMapping("/scenes")
    public ResponseEntity<Map<String, List<PetSceneResponseDTO>>> getScenes(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        validateSession(body);
        return ResponseEntity.ok(Map.of("scenes", petSceneService.getAllScenes()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, List<PetNotificationResponseDTO>>> getNotifications(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        validateSession(body);
        return ResponseEntity.ok(Map.of("notifications", petNotificationService.getAllNotifications()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(
            @PathVariable String id,
            @RequestBody PetSessionRequestDTO body
    ) {
        validateSession(body);
        petNotificationService.markNotificationAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Valida la sesión fallando con un 400 explícito si las credenciales no llegaron.
     *
     * Antes esto era `validateSession(body.sessionToken(), ...)` directo, y un body
     * ausente reventaba con NPE → 500 "Unexpected error", que no dice nada.
     *
     * El caso que lo dispara en la práctica: estos endpoints son GET y el juego manda
     * las credenciales en el body. Los navegadores DESCARTAN el body de las peticiones
     * GET (fetch lanza TypeError; XHR lo pone en null), así que llega vacío. Funciona
     * con curl y en el editor de Unity, y falla solo en WebGL — por eso el mensaje
     * nombra la causa: diagnosticarlo a ciegas cuesta días.
     */
    private void validateSession(PetSessionRequestDTO body) {
        if (body == null || body.sessionToken() == null || body.userHash() == null) {
            throw new InvalidRequestException(
                    "Faltan session_token y/o user_hash. Si los enviaste en el body de un GET, "
                            + "el navegador los descarta: usa POST o mándalos como query params.");
        }
        petSessionService.validateSession(body.sessionToken(), body.userHash());
    }

    private Long getConsumerId(Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return consumerDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Consumer not found for userId=" + userId))
                .getId();
    }
}
