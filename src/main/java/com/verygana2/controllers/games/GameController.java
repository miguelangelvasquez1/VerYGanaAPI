package com.verygana2.controllers.games;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;
import com.verygana2.services.interfaces.GameService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
@Validated
@Slf4j
public class GameController {

    private final GameService gameService;
    
    @PostMapping("/init")
    public ResponseEntity<InitGameResponseDTO> initGame(InitGameRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
        InitGameResponseDTO response = gameService.initGameSession(request, jwt.getClaim("userId"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/metrics")
    public ResponseEntity<Void> submitGameMetrics(@RequestBody GameEventDTO<List<GameMetricDTO>> event, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        gameService.submitGameMetrics(event.getSessionId(), event.getPayload(), userId);
        return ResponseEntity.ok().build();
    }

    //Tipos de datos que retornan estos metodos y parametros aun no definidos
    //@GetMapping
    //public PagedResponse<Game> getAvailableGamesPage (){
    //};

    //@GetMapping("/{gameId}")
    //public Game getGameDetails (@PathVariable Long gameId){
    //};

    //@GetMapping("{gameId}/config")
    //public GameConfigDTO getGameConfig (@PathVariable Long gameId){
    //}
}
