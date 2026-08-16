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

    /**
     * Token de sesión reservado para el modo preview del diseñador. Mismo valor y
     * mismo criterio que en juegos (ver GameController.getGameAssets).
     */
    private static final String PREVIEW_TOKEN = "preview";

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


    // POST y no GET aunque sean lecturas: las credenciales viajan en el body, y los
    // navegadores descartan el body de las peticiones GET (fetch lanza TypeError, XHR lo
    // ignora), así que desde WebGL nunca llegaban. Mismo criterio que /games/assets.
    @PostMapping("/catalog")
    public ResponseEntity<Map<String, List<PetCatalogItemResponseDTO>>> getCatalog(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        validateSession(body);
        return ResponseEntity.ok(Map.of("foods", petCatalogService.getAllCatalogItems()));
    }

    // "/scenes-objects" es un alias: el build de Unity tiene esa ruta horneada y su
    // network_config.json solo aplica baseUrl, así que no se puede redirigir desde
    // configuración. Se puede quitar cuando el juego respete el endpoint del archivo.
    @PostMapping({"/scenes", "/scenes-objects"})
    public ResponseEntity<Map<String, List<PetSceneResponseDTO>>> getScenes(
            @RequestBody(required = false) PetSessionRequestDTO body
    ) {
        // Modo preview: el diseñador abre el juego contra un borrador para ver dónde
        // queda cada objeto antes de publicarlo. No hay sesión de mascota que validar
        // —no es un consumidor jugando—, así que se atiende antes de validateSession.
        if (body != null && PREVIEW_TOKEN.equals(body.sessionToken())) {
            return ResponseEntity.ok(Map.of(
                    "scenes", petSceneService.getScenesForPreview(previewSceneId(body.userHash()))));
        }

        validateSession(body);
        return ResponseEntity.ok(Map.of("scenes", petSceneService.getAllScenes()));
    }

    @PostMapping("/notifications")
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

    /**
     * En preview, `user_hash` transporta la escena a previsualizar.
     *
     * Se reutiliza ese campo porque el build solo manda `session_token` y `user_hash`:
     * no hay un tercer parámetro que añadir sin volver a tocar Unity, y el `user_hash`
     * no significa nada cuando no hay consumidor detrás. Juegos hace lo mismo mandando
     * `user_hash=preview` junto al `campaign_id`.
     *
     * Cualquier valor no numérico (incluido el literal "preview") pide todas las
     * escenas, publicadas y borradores.
     */
    private static Integer previewSceneId(String userHash) {
        if (userHash == null || userHash.isBlank()) return null;
        try {
            return Integer.valueOf(userHash.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getConsumerId(Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return consumerDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Consumer not found for userId=" + userId))
                .getId();
    }
}
