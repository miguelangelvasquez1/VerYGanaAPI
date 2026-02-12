package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;

public interface GameService {
    
    String initGameSponsored(InitGameRequestDTO request, Long userId);
    String initGameNotSponsored(InitGameRequestDTO request, Long userId);
    ObjectNode getGameAssets(GameEventDTO<Void> req);
    void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event);
    void completeSession(GameEventDTO<EndSessionDTO> event, Long userId);
    PagedResponse<GameDTO> getAvailableGamesPage (Pageable pageable);
}
