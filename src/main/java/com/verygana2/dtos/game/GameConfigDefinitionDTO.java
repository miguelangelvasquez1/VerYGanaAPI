package com.verygana2.dtos.game;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConfigDefinitionDTO {
    
    private String jsonKey;
    private boolean required;
    private String description;

    /** JSON schema (tal cual) */
    private Map<String, Object> schema;
}
