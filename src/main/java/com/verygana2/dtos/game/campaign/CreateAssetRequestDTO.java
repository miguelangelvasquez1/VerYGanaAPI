package com.verygana2.dtos.game.campaign;

import com.verygana2.dtos.FileUploadRequestDTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssetRequestDTO {
    
    @NotNull(message = "Debe elegir un asset preestablecido")
    private Long assetDefinitionId;

    // @NotNull(message = "Debe elegir una campaña")
    // private Long campaignId;

    private FileUploadRequestDTO fileMetadata;
}
