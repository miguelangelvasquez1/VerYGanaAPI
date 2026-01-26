package com.verygana2.controllers;

import java.util.List;

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

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;
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

    /**
     * Flujo:
     * 1. El juego se inicia con los parámetros de la url
     * 2. El juego lee los parámetros y llama al backend para pedir los assets y configuración
     * 3. El juego recibe los assets y configuración y empieza su ejecución
     * */

    
    @PostMapping("/init")
    public ResponseEntity<InitGameResponseDTO> initGame(@Valid @RequestBody InitGameRequestDTO request, @AuthenticationPrincipal Jwt jwt) {

        // Init sponsored game
        if (request.getSponsored() != null && request.getSponsored()) {
            InitGameResponseDTO response = gameService.initGameSponsored(request, jwt.getClaim("userId"));
            return ResponseEntity.ok(response);

        }
        // Init not sponsored game
        InitGameResponseDTO response = gameService.initGameNotSponsored(request, jwt.getClaim("userId"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/metrics")
    public ResponseEntity<Void> submitGameMetrics(@RequestBody GameEventDTO<List<GameMetricDTO>> event, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        gameService.submitGameMetrics(event, userId);
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

    // //Método para que el juego obtenga los assets
    // @GetMapping("/{gameId}/assets")
    // public ResponseEntity<List<String>> getGameAssets(@PathVariable Long gameId) {
    //     List<String> assets = gameService.getGameAssets(gameId);
    //     return ResponseEntity.ok(assets);
    // }

    //@GetMapping("/{gameId}")
    //public Game getGameDetails (@PathVariable Long gameId){
    //};

    //@GetMapping("{gameId}/config")
    //public GameConfigDTO getGameConfig (@PathVariable Long gameId){
    //}

    //GetMetrics by sessionId

    //GetSession details
}
