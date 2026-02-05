package com.verygana2.dtos.game;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConfigDTO {

    private Map<String, Object> game_config;
    private Map<String, Object> colors;
    private Map<String, Object> texts;
    private Map<String, Object> rewards;
    private Map<String, Object> game;


}
