package com.verygana2.dtos.game;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEventDTO<T> {
    
    // La credencial de la sesión no debe salir en un toString (log futuro)
    @ToString.Exclude
    private String sessionToken;
    @ToString.Exclude
    private String userHash;
    private Boolean isBrandedMode;
    private Long campaignId;
    private String gameTitle; // Titulo del juego
    private T payload; // Métricas periódicas, métricas finales, pedir assets?, etc.\
    private JsonNode technicalData; // Errores, load_time, etc
}
