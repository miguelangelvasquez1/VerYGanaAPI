package com.verygana2.dtos.game.campaign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSchemaResponse {
    
    private Long gameId;
    private String gameName;
    private String version;
    private Object jsonSchema;
    private Object uiSchema;
    
}
