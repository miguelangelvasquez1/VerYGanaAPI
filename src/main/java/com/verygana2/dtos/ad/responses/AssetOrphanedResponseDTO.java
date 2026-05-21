package com.verygana2.dtos.ad.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetOrphanedResponseDTO {
    private Long assetId;
    private String message;
}
 