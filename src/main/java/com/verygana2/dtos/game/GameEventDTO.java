package com.verygana2.dtos.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEventDTO<T> {
    
    private Long sessionId;
    private Long gameId;
    private String eventType; // Inicio de juego, eventos de gameplay, métricas finales.
    private T payload; // Métricas
}
