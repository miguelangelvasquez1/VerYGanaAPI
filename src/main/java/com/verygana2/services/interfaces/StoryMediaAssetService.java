package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.impactStory.PrepareMediaUploadRequestDTO;
import com.verygana2.dtos.impactStory.PrepareMediaUploadResponseDTO;
import com.verygana2.models.ImpactStory.StoryMediaAsset;

public interface StoryMediaAssetService {
 
    PrepareMediaUploadResponseDTO prepareUpload(PrepareMediaUploadRequestDTO request);
    List<StoryMediaAsset> validateAndClaimAssets(List<Long> assetIds);
    void markOrphaned(List<Long> assetIds);
}
