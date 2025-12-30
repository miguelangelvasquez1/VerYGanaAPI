package com.verygana2.dtos.game.campaign;

import java.util.Set;

import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;

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
    private Set<SupportedMimeType> allowedMimeTypes;
    private boolean required;
    private boolean multiple;
    private String description;

    // Poner resolution, validaciones, etc.
}
