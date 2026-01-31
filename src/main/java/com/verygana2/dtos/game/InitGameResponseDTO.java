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
public class InitGameResponseDTO { // DTO de respuesta con los assets
    
    private String sessionToken;
    private String userHash;
    private Long gameId;
    private Long campaignId;
    private List<AssetDTO> assets;
    private String jsonConfig; // Opcional: configuración adicional en formato JSON/Base64
}
