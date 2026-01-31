package com.verygana2.dtos.game;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConfigDTO {

    private MetaDTO meta;

    private Map<String, Object> branding;
    private Map<String, Object> puzzle;
    private Map<String, Object> audio;
    private Map<String, Object> texts;
    private Map<String, Object> rewards;
}
