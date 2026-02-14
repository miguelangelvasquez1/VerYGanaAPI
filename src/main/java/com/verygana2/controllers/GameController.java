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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verygana2.controllers.gameAssetsBorrar.AvoidTheBombAssets;
import com.verygana2.controllers.gameAssetsBorrar.BallBounceAssets;
import com.verygana2.controllers.gameAssetsBorrar.BalloonLiftAssets;
import com.verygana2.controllers.gameAssetsBorrar.CatchItAssets;
import com.verygana2.controllers.gameAssetsBorrar.HangmanAssets;
import com.verygana2.controllers.gameAssetsBorrar.Match3Assets;
import com.verygana2.controllers.gameAssetsBorrar.MemoryAssets;
import com.verygana2.controllers.gameAssetsBorrar.SudokuAssets;
import com.verygana2.controllers.gameAssetsBorrar.TapToRotateAssets;
import com.verygana2.controllers.gameAssetsBorrar.WhackAMoleAssets;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
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
        // Json
        if (req.getCampaignId() != null && req.getCampaignId() == 1L) {
            return ResponseEntity.ok(HangmanAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 2L) {
            return ResponseEntity.ok(TapToRotateAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 3L) {
            return ResponseEntity.ok(MemoryAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 4L) {
            return ResponseEntity.ok(SudokuAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 5L) {
            return ResponseEntity.ok(Match3Assets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 6L) {
            return ResponseEntity.ok(BalloonLiftAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 7L) {
            return ResponseEntity.ok(AvoidTheBombAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 8L) {
            return ResponseEntity.ok(BallBounceAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 9L) {
            return ResponseEntity.ok(WhackAMoleAssets.ASSETS);
        } else if (req.getCampaignId() != null && req.getCampaignId() == 10L) {
            return ResponseEntity.ok(CatchItAssets.ASSETS);
        }
        return ResponseEntity.badRequest().body(null);
        // log.info(req.toString());
        // ObjectNode assets = gameService.getGameAssets(req);
        // return ResponseEntity.ok(assets);
    }

    @PostMapping("/metrics")
    public ResponseEntity<Void> submitGameMetrics(@RequestBody GameEventDTO<List<GameMetricDTO>> event, @AuthenticationPrincipal Jwt jwt) {
        // Long userId = jwt.getClaim("userId");
        if (event.getIsBrandedMode() == false) return ResponseEntity.ok().build();
        System.out.println(event.toString());
        gameService.submitGameMetrics(event);
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

    //@GetMapping("/{gameId}")
    //public Game getGameDetails (@PathVariable Long gameId){
    //};

    //@GetMapping("{gameId}/config")
    //public GameConfigDTO getGameConfig (@PathVariable Long gameId){
    //}

    //GetMetrics by sessionId

    //GetSession details
}
