package com.verygana2.dtos.game.campaign;

import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameAssetDefinitionDTO {
    
    private Long id;
    private AssetType assetType;
    private MediaType mediaType;
    private boolean required;
    private boolean multiple;
    private String description;
}
