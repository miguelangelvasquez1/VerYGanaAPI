package com.verygana2.dtos.game;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitGameResponseDTO {
    
    // Info from game
    private Long gameId;
    private String gameCode;
    private String gameName;
    private Integer minDurationSeconds; // Optional en config
    private Integer maxDurationSeconds; // Optional

    private Long sessionId;
    private Long campaignId;
    private String advertiserName;
    private List<AssetDTO> assets;
    private String jsonConfig; // Opcional: configuración adicional en formato JSON/Base64
}
