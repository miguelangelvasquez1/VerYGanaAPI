package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.CreateStoryMediaRequestDTO;
import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.mappers.ImpactStoryMapper;
import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryStatus;
import com.verygana2.repositories.ImpactStoryRepository;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;

/**
 * Reglas de estado de las historias de impacto:
 * <ul>
 *   <li>una historia DELETED no existe para nadie (404) — sus archivos ya no están en el CDN;</li>
 *   <li>un consumidor solo ve las PUBLISHED; un admin también DRAFT y ARCHIVED;</li>
 *   <li>DELETED solo se alcanza con {@code delete}, que es lo que libera los archivos: asignarlo por
 *       create/update ocultaría la historia dejando su media viva en R2.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImpactStoryServiceImpl — estados y visibilidad")
class ImpactStoryServiceImplStatusTest {

    @Mock private ImpactStoryRepository storyRepository;
    @Mock private StoryMediaAssetServiceImpl mediaAssetService;
    @Mock private ImpactStoryMapper mapper;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;

    @InjectMocks private ImpactStoryServiceImpl service;

    private void givenStory(StoryStatus status) {
        ImpactStory story = ImpactStory.builder().id(1L).title("Historia").status(status)
                .mediaFiles(new ArrayList<>()).build();
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
        lenient().when(mapper.toResponse(story)).thenReturn(new ImpactStoryResponseDTO());
    }

    // ─── GET /impact-stories/{id} ───────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("una historia DELETED es 404 incluso para un admin")
        void deleted_isNotFoundForEveryone() {
            givenStory(StoryStatus.DELETED);

            assertThatThrownBy(() -> service.findById(1L, true)).isInstanceOf(EntityNotFoundException.class);
            assertThatThrownBy(() -> service.findById(1L, false)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("un consumidor no ve borradores ni archivadas: 404 (no 403), igual que un id inexistente")
        void draftAndArchived_areNotFoundForNonAdmin() {
            givenStory(StoryStatus.DRAFT);
            assertThatThrownBy(() -> service.findById(1L, false))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("ImpactStory not found: 1");
        }

        @Test
        @DisplayName("un consumidor tampoco ve una ARCHIVED")
        void archived_isNotFoundForNonAdmin() {
            givenStory(StoryStatus.ARCHIVED);
            assertThatThrownBy(() -> service.findById(1L, false)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("un consumidor sí ve una PUBLISHED")
        void published_isVisibleToNonAdmin() {
            givenStory(StoryStatus.PUBLISHED);
            assertThat(service.findById(1L, false)).isNotNull();
        }

        @Test
        @DisplayName("un admin ve DRAFT, PUBLISHED y ARCHIVED")
        void adminSeesNonDeletedStories() {
            givenStory(StoryStatus.DRAFT);
            assertThat(service.findById(1L, true)).isNotNull();
        }

        @Test
        @DisplayName("un id que no existe sigue siendo 404")
        void unknownId_isNotFound() {
            when(storyRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(99L, true)).isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ─── DELETED solo por delete() ──────────────────────────────────────────

    @Nested
    @DisplayName("asignar DELETED por create/update")
    class AssigningDeleted {

        @Test
        @DisplayName("update con status DELETED: 409 y no cambia nada (ocultaría la historia sin liberar su media)")
        void update_toDeleted_isRejected() {
            givenStory(StoryStatus.PUBLISHED);
            UpdateImpactStoryRequestDTO toDeleted = UpdateImpactStoryRequestDTO.builder()
                    .status(StoryStatus.DELETED).build();

            assertThatThrownBy(() -> service.update(1L, toDeleted))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("DELETE /impact-stories/{id}");

            verify(mapper, never()).updateEntity(any(), any());
            verify(storyRepository, never()).save(any());
        }

        @Test
        @DisplayName("create con status DELETED: 409 y no toca ningún asset")
        void create_asDeleted_isRejected() {
            CreateImpactStoryRequestDTO asDeleted = CreateImpactStoryRequestDTO.builder()
                    .title("Historia").status(StoryStatus.DELETED)
                    .mediaFiles(List.of(CreateStoryMediaRequestDTO.builder().mediaAssetId("5").build()))
                    .build();

            assertThatThrownBy(() -> service.create(asDeleted)).isInstanceOf(InvalidStatusException.class);

            verify(mediaAssetService, never()).validateAndClaimAssets(any());
            verify(storyRepository, never()).save(any());
        }
    }
}
