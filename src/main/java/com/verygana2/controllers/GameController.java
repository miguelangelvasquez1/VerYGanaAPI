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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verygana2.controllers.gameAssetsBorrar.DashRunnerAssets;
import com.verygana2.controllers.gameAssetsBorrar.EndlessRunnerAssets;
import com.verygana2.controllers.gameAssetsBorrar.MemoryMatchAssets;
import com.verygana2.controllers.gameAssetsBorrar.MiniFlappyAssets;
import com.verygana2.controllers.gameAssetsBorrar.SimpleCrosswordAssets;
import com.verygana2.controllers.gameAssetsBorrar.StackTowerAssets;
import com.verygana2.controllers.gameAssetsBorrar.TicTacToeAssets;
import com.verygana2.controllers.gameAssetsBorrar.TilePuzzleAssets;
import com.verygana2.controllers.gameAssetsBorrar.TriviaQuizAssets;
import com.verygana2.controllers.gameAssetsBorrar.WordSearchAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.AvoidTheBombAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.BallBounceAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.BalloonLiftAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.CatchItAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.HangmanAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.Match3Assets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.MemoryAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.SudokuAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.TapToRotateAssets;
// import com.verygana2.controllers.gameAssetsBorrar.cali.WhackAMoleAssets;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.services.interfaces.GameService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
@Validated
@Slf4j
public class GameController {

    private final GameService gameService;
    
    // Devuelve la url del juego armada con los parametros necesarios
    @PostMapping("/init")
    public ResponseEntity<Map<String, String>> initGame(@Valid @RequestBody InitGameRequestDTO request, @AuthenticationPrincipal Jwt jwt) {

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
        
        // if (req.getCampaignId() != null && req.getCampaignId() == 1L) {
        //     return ResponseEntity.ok(TapToRotateAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 2L) {
        //     return ResponseEntity.ok(MemoryAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 3L) {
        //     return ResponseEntity.ok(HangmanAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 4L) {
        //     return ResponseEntity.ok(SudokuAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 5L) {
        //     return ResponseEntity.ok(Match3Assets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 6L) {
        //     return ResponseEntity.ok(BalloonLiftAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 7L) {
        //     return ResponseEntity.ok(AvoidTheBombAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 8L) {
        //     return ResponseEntity.ok(BallBounceAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 9L) {
        //     return ResponseEntity.ok(WhackAMoleAssets.ASSETS);
        // } else if (req.getCampaignId() != null && req.getCampaignId() == 10L) {
        //     return ResponseEntity.ok(CatchItAssets.ASSETS);
            
        // ==================== NUEVOS JUEGOS (11 al 20) ====================
        
         if (req.getCampaignId() != null && req.getCampaignId() == 1L) {
            return ResponseEntity.ok(MiniFlappyAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 2L) {
            return ResponseEntity.ok(EndlessRunnerAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 3L) {
            return ResponseEntity.ok(TriviaQuizAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 4L) {
            return ResponseEntity.ok(StackTowerAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 5L) {
            return ResponseEntity.ok(MemoryMatchAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 6L) {
            return ResponseEntity.ok(WordSearchAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 7L) {
            return ResponseEntity.ok(DashRunnerAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 8L) {
            return ResponseEntity.ok(SimpleCrosswordAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 9L) {
            return ResponseEntity.ok(TicTacToeAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 10L) {
            return ResponseEntity.ok(TilePuzzleAssets.ASSETS);
        }

        // Si no coincide ningún campaign_id
        return ResponseEntity.badRequest().body(null);
    }

    @PostMapping("/metrics")
    public ResponseEntity<Void> submitGameMetrics(@RequestBody GameEventDTO<List<GameMetricDTO>> event, @AuthenticationPrincipal Jwt jwt) {
        // Long userId = jwt.getClaim("userId");
        if (event.getIsBrandedMode() == false) return ResponseEntity.ok().build();
        System.out.println(event.toString());
        // gameService.submitGameMetrics(event);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/end-session")
    public ResponseEntity<Void> endSession(@RequestBody GameEventDTO<EndSessionDTO> event, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        gameService.completeSession(event, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public PagedResponse<GameDTO> getAvailableGamesPage (@PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC ) Pageable pageable) {
        return gameService.getAvailableGamesPage(pageable);
    }

    /**
     * Get JSON Schema for a specific game
     * 
     * GET /api/games/{id}/schema
     * Response: { "gameId": 1, "version": "1.0.0", "jsonSchema": {...}, "uiSchema": {...} }
     */
    @GetMapping("/{id}/schema")
    public ResponseEntity<GameSchemaResponse> getGameSchema(@PathVariable Long id) {
        log.info("Getting schema for game: {}", id);
        
        GameSchemaResponse response = gameService.getLatestGameSchema(id);
        return ResponseEntity.ok(response);
    }

    //GetMetrics by sessionId

    //GetSession details
}
