package com.verygana2.services.interfaces;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;

public interface GameService {

    GameSchemaResponse getLatestGameSchema(Long gameId);
    
    String initGameSponsored(InitGameRequestDTO request, Long userId);
    String initGameNotSponsored(InitGameRequestDTO request, Long userId);
    Map<String,Object> getGameAssets(GameEventDTO<Void> req);
    void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event);
    void completeSession(GameEventDTO<EndSessionDTO> event, Long userId);
    PagedResponse<GameDTO> getAvailableGamesPage (Pageable pageable);
}
