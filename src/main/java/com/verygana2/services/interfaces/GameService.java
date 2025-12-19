package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;

public interface GameService {
    
    InitGameResponseDTO initGameSession(InitGameRequestDTO request, Long userId);
    void submitGameMetrics(Long sessionId, List<GameMetricDTO> metrics, Long userId);
}
