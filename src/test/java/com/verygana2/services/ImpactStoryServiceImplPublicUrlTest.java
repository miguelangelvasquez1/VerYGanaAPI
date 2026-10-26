package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.CreateStoryMediaRequestDTO;
import com.verygana2.mappers.ImpactStoryMapper;
import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.repositories.ImpactStoryRepository;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.PublicUrlBuilder;
import com.verygana2.storage.service.R2Service;
import com.verygana2.testsupport.PublicUrlBuilders;

/** Al crear una historia, la URL que se guarda en el asset sale del builder (CDN del ambiente). */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImpactStoryServiceImpl.create — URL pública")
class ImpactStoryServiceImplPublicUrlTest {

    @Mock private ImpactStoryRepository storyRepository;
    @Mock private StoryMediaAssetServiceImpl mediaAssetService;
    @Mock private ImpactStoryMapper mapper;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;
    @Spy private PublicUrlBuilder publicUrlBuilder = PublicUrlBuilders.forDomain("cdn-beta.verygana.com");

    @InjectMocks private ImpactStoryServiceImpl service;

    @Test
    @DisplayName("el publicUrl grabado usa el CDN configurado")
    void createStoresUrlWithConfiguredCdn() {
        StoryMediaAsset asset = new StoryMediaAsset();
        asset.setId(1L);
        asset.setObjectKey("impact/a.jpg");
        when(mediaAssetService.validateAndClaimAssets(List.of(1L))).thenReturn(List.of(asset));
        when(mapper.toEntity(any())).thenReturn(new ImpactStory());
        when(r2Service.detectRealMimeType("public/impact/a.jpg")).thenReturn("image/jpeg");
        when(storyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(CreateImpactStoryRequestDTO.builder()
                .title("Historia")
                .mediaFiles(List.of(CreateStoryMediaRequestDTO.builder().mediaAssetId("1").fileName("f.jpg").build()))
                .build());

        assertThat(asset.getPublicUrl()).isEqualTo("https://cdn-beta.verygana.com/public/impact/a.jpg");
    }
}
