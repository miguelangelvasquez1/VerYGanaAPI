package com.verygana2.dtos.game.campaign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedAssetDTO {
    private Long assetId;
    private String publicUrl;
    private String mediaType;
}
