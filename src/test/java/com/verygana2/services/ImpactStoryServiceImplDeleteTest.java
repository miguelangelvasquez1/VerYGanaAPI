package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.mappers.ImpactStoryMapper;
import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ImpactStory.StoryStatus;
import com.verygana2.repositories.ImpactStoryRepository;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

/**
 * Borrar una historia es definitivo: su media queda ORPHANED y {@code OrphanedAssetsCleanupJob}
 * la borra de R2 sin papelera. Por eso una historia DELETED no puede volver a modificarse: un
 * update que la devolviera a PUBLISHED/DRAFT la haría reaparecer con todas sus imágenes rotas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImpactStoryServiceImpl — borrar es definitivo")
class ImpactStoryServiceImplDeleteTest {

    @Mock private ImpactStoryRepository storyRepository;
    @Mock private StoryMediaAssetServiceImpl mediaAssetService;
    @Mock private ImpactStoryMapper mapper;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;

    @InjectMocks private ImpactStoryServiceImpl service;

    private static ImpactStory storyWith(StoryStatus status, Long... mediaIds) {
        ImpactStory story = ImpactStory.builder().id(1L).title("Historia").status(status)
                .mediaFiles(new ArrayList<>()).build();
        for (Long mediaId : mediaIds) {
            story.getMediaFiles().add(StoryMediaAsset.builder().id(mediaId).impactStory(story).build());
        }
        return story;
    }

    @Test
    @DisplayName("update sobre una historia DELETED: 409 (InvalidStatusException) y no modifica nada, aunque intente volverla PUBLISHED")
    void update_ofDeletedStory_isRejected() {
        ImpactStory deleted = storyWith(StoryStatus.DELETED, 10L);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(deleted));

        UpdateImpactStoryRequestDTO restore = UpdateImpactStoryRequestDTO.builder()
                .status(StoryStatus.PUBLISHED).build();

        assertThatThrownBy(() -> service.update(1L, restore))
                .isInstanceOf(InvalidStatusException.class)
                .hasMessageContaining("eliminada");

        verify(mapper, never()).updateEntity(any(), any());
        verify(storyRepository, never()).save(any());
        assertThat(deleted.getStatus()).isEqualTo(StoryStatus.DELETED);
    }

    @Test
    @DisplayName("update sobre una historia no borrada sigue funcionando")
    void update_ofPublishedStory_stillWorks() {
        ImpactStory published = storyWith(StoryStatus.PUBLISHED);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(published));
        when(storyRepository.save(published)).thenReturn(published);
        when(mapper.toResponse(published)).thenReturn(new ImpactStoryResponseDTO());

        UpdateImpactStoryRequestDTO archive = UpdateImpactStoryRequestDTO.builder()
                .status(StoryStatus.ARCHIVED).build();
        service.update(1L, archive);

        verify(mapper).updateEntity(published, archive);
        verify(storyRepository).save(published);
    }

    @Test
    @DisplayName("delete deja la historia DELETED y orfana cada archivo de su media (el job los borra de R2)")
    void delete_marksStoryDeletedAndOrphansEveryMedia() {
        ImpactStory story = storyWith(StoryStatus.PUBLISHED, 10L, 11L);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

        service.delete(1L);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.DELETED);
        verify(mediaAssetService).markOrphaned(List.of(10L));
        verify(mediaAssetService).markOrphaned(List.of(11L));
        verify(storyRepository).save(story);
    }
}
