package com.verygana2.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.services.interfaces.GameService;

import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
@Validated
@Slf4j
public class GameController {

    private final GameService gameService;
    private final ObjectMapper objectMapper;

    // Devuelve la url del juego armada con los parametros necesarios
    @PostMapping("/init")
    public ResponseEntity<Map<String, String>> initGame(@Valid @RequestBody InitGameRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        // Init sponsored game
        if (request.getSponsored() != null && request.getSponsored()) {
            String response = gameService.initGameSponsored(request, jwt.getClaim("userId"));
            log.info(response);
            return ResponseEntity.ok(Map.of("url", response));
        }

        // Init not sponsored game
        String response = gameService.initGameNotSponsored(request, jwt.getClaim("userId"));
        log.info(response);
        return ResponseEntity.ok(Map.of("url", response));
    }

    // Método para que el juego obtenga los assets
    @PostMapping("/assets")
    public ResponseEntity<ObjectNode> getGameAssets(@RequestBody GameEventDTO<Void> req) {

        // Preview mode: session_token=preview siempre identifica un BrandingRequest,
        // nunca una campaña real
        if ("preview".equals(req.getSessionToken())) {
            if (req.getCampaignId() == null) {
                throw new ValidationException("campaign_id es requerido para la preview");
            }
            // Sin try/catch a propósito: el GlobalExceptionHandler responde con cuerpo y
            // estado. Capturarlo acá devolvía 400 con cuerpo null, y ni el juego ni quien
            // depuraba tenían forma de saber qué había fallado.
            return ResponseEntity.ok(objectMapper.valueToTree(gameService.getPreviewAssets(req.getCampaignId())));
        }

        // Partida real: el service valida la sesión y saca de ella la campaña.
        return ResponseEntity.ok(objectMapper.valueToTree(gameService.getGameAssets(req)));
    }

    @PostMapping("/metrics")
    public ResponseEntity<Void> submitGameMetrics(@RequestBody GameEventDTO<List<GameMetricDTO>> event,
            @AuthenticationPrincipal Jwt jwt) {
        // Long userId = jwt.getClaim("userId");
        if (event.getIsBrandedMode() == false)
            return ResponseEntity.ok().build();
        System.out.println(event.toString());
        // gameService.submitGameMetrics(event);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/end-session")
    public ResponseEntity<Void> endSession(@RequestBody GameEventDTO<EndSessionDTO> event,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        gameService.completeSession(event, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public PagedResponse<GameDTO> getAvailableGamesPage(
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return gameService.getAvailableGamesPage(pageable);
    }

    /**
     * Get JSON Schema for a specific game
     * 
     * GET /api/games/{id}/schema
     * Response: { "gameId": 1, "version": "1.0.0", "jsonSchema": {...}, "uiSchema":
     * {...} }
     */
    @GetMapping("/{id}/schema")
    public ResponseEntity<GameSchemaResponse> getGameSchema(@PathVariable Long id) {
        log.info("Getting schema for game: {}", id);

        GameSchemaResponse response = gameService.getLatestGameSchema(id);
        return ResponseEntity.ok(response);
    }

    // GetMetrics by sessionId

    // GetSession details
}
