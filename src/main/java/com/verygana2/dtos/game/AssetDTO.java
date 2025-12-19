package com.verygana2.dtos.game;

import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetDTO {
    
    private Long id;
    private String url;
    private AssetType assetType;
    private MediaType mediaType;
}
    