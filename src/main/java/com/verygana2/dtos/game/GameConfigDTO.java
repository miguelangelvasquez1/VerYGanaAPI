package com.verygana2.dtos.game;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConfigDTO {

    private Map<String, Object> config;
    private Map<String, String> colors;
    private Map<String, String> texts;
    private Map<String, Object> specifications;/** Configuración específica del juego (libre) */
}
