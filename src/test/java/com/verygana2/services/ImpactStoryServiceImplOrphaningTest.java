package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.CreateStoryMediaRequestDTO;
import com.verygana2.mappers.ImpactStoryMapper;
import com.verygana2.repositories.ImpactStoryRepository;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

/**
 * Si crear una historia falla, la media que ya se subió a R2 debe quedar ORPHANED para que el
 * barrido la borre. {@code ImpactStoryServiceImpl} es transaccional y relanza la excepción, así
 * que un marcado hecho en su propia transacción se revierte: tiene que ir por
 * {@link AssetOrphanedService} (REQUIRES_NEW, y solo sobre lo que sigue sin vincular).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImpactStoryServiceImpl.create — media de una creación fallida")
class ImpactStoryServiceImplOrphaningTest {

    @Mock private ImpactStoryRepository storyRepository;
    @Mock private StoryMediaAssetServiceImpl mediaAssetService;
    @Mock private ImpactStoryMapper mapper;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;

    @InjectMocks private ImpactStoryServiceImpl service;

    private static CreateImpactStoryRequestDTO requestWithMedia(String... assetIds) {
        return CreateImpactStoryRequestDTO.builder()
                .title("Historia")
                .mediaFiles(java.util.Arrays.stream(assetIds)
                        .map(id -> CreateStoryMediaRequestDTO.builder().mediaAssetId(id).fileName("f.jpg").build())
                        .toList())
                .build();
    }

    @Test
    @DisplayName("falla al validar/reclamar los assets: se orfanan por AssetOrphanedService y se relanza la excepción")
    void claimFailure_orphansThroughAssetOrphanedService() {
        when(mediaAssetService.validateAndClaimAssets(List.of(1L, 2L)))
                .thenThrow(new IllegalStateException("Asset 1 no está en estado PENDING"));

        assertThatThrownBy(() -> service.create(requestWithMedia("1", "2")))
                .isInstanceOf(IllegalStateException.class);

        verify(assetOrphanedService).markImpactStoryAssetsAsOrphanedByIds(List.of(1L, 2L));
        verify(mediaAssetService, never()).markOrphaned(any());
    }

    @Test
    @DisplayName("falla al armar la historia: también se orfanan por AssetOrphanedService")
    void buildFailure_orphansThroughAssetOrphanedService() {
        when(mediaAssetService.validateAndClaimAssets(List.of(3L))).thenReturn(List.of());
        when(mapper.toEntity(any())).thenThrow(new IllegalArgumentException("mapeo inválido"));

        assertThatThrownBy(() -> service.create(requestWithMedia("3")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(assetOrphanedService).markImpactStoryAssetsAsOrphanedByIds(List.of(3L));
        verify(mediaAssetService, never()).markOrphaned(any());
    }
}
