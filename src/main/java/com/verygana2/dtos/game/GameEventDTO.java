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
    private T payload; // Métricas periódicas, métricas finales, etc.
}
