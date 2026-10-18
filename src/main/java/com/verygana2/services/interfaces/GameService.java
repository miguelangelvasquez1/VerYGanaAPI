package com.verygana2.services.interfaces;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.EndSessionResponseDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.models.branding.BrandingRequest;

public interface GameService {

    GameSchemaResponse getLatestGameSchema(Long gameId);

    String generatePreviewUrl(BrandingRequest brandingRequest);
    Map<String, Object> getPreviewAssets(Long brandingRequestId);
    
    String initGameSponsored(InitGameRequestDTO request, Long userId);
    String initGameNotSponsored(InitGameRequestDTO request, Long userId);
    Map<String,Object> getGameAssets(GameEventDTO<Void> req);
    /**
     * Cierra la partida y la paga (cobro a la campaña, llaves, XP) con el puntaje de la métrica
     * {@code performance}. Una sesión ya cerrada no hace nada (idempotente, sin pagar de nuevo).
     */
    void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event);
    /** Lo usa el endpoint obsoleto {@code /games/end-session}; un segundo cierre responde 422. */
    EndSessionResponseDTO completeSession(GameEventDTO<EndSessionDTO> event);
    PagedResponse<GameDTO> getAvailableGamesPage (Pageable pageable);
}
