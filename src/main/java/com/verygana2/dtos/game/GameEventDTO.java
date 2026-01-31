package com.verygana2.dtos.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEventDTO<T> {
    
    private String sessionToken;
    private String userHash;
    private Boolean isBrandedMode;
    private Long campaignId;
    private String gameId; // Titulo del juego
    private T payload; // Métricas periódicas, métricas finales, pedir assets?, etc.\
    private String technicalData; // Errores, load_time, etc
}
