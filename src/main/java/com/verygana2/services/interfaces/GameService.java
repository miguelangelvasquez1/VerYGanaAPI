package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;

public interface GameService {
    
    InitGameResponseDTO initGameSponsored(InitGameRequestDTO request, Long userId);
    InitGameResponseDTO initGameNotSponsored(InitGameRequestDTO request, Long userId);
    void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event, Long userId);
    void completeSession(GameEventDTO<EndSessionDTO> event, Long userId);
    PagedResponse<GameDTO> getAvailableGamesPage (Pageable pageable);
}
