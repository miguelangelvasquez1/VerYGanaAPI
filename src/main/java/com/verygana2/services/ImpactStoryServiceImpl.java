package com.verygana2.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.mappers.ImpactStoryMapper;
import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ImpactStory.StoryStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.repositories.ImpactStoryRepository;
import com.verygana2.services.interfaces.ImpactStoryService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ImpactStoryServiceImpl implements ImpactStoryService {

    private final ImpactStoryRepository storyRepository;
    private final StoryMediaAssetServiceImpl mediaAssetService;
    private final ImpactStoryMapper mapper;
    private final R2Service r2Service;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * PASO 3 del flujo de media:
     * Recibe los mediaAssetIds ya subidos a R2, los valida (PENDING → VALIDATED),
     * aplica los metadatos editoriales del DTO sobre cada StoryMediaAsset
     * y los asocia directamente a la historia.
     */
    @Override
    public ImpactStoryResponseDTO create(CreateImpactStoryRequestDTO request) {

        List<Long> assetIds = request.getMediaFiles() == null
            ? List.of()
            : request.getMediaFiles().stream()
                .map(m -> Long.parseLong(
                    Objects.requireNonNull(m.getMediaAssetId(), "mediaAssetId is required")
                ))
                .toList();

        // ── PASO 3a: Validar StoryMediaAssets (PENDING → VALIDATED) ──────────
        List<StoryMediaAsset> validatedAssets;
        try {
            validatedAssets = assetIds.isEmpty()
                ? List.of()
                : mediaAssetService.validateAndClaimAssets(assetIds);

        } catch (Exception e) {
            log.error("Error validando assets al crear historia: {}", assetIds, e);
            if (!assetIds.isEmpty()) {
                mediaAssetService.markOrphaned(assetIds);
            }
            throw e;
        }

        // ── PASO 3b: Crear historia y asociar assets ──────────────────────────
        try {
            ImpactStory story = mapper.toEntity(request);

            // mediaAssetId (String) → StoryMediaAsset para lookup O(1)
            Map<String, StoryMediaAsset> assetMap = validatedAssets.stream()
                .collect(Collectors.toMap(
                    a -> String.valueOf(a.getId()),
                    a -> a
                ));

            // Aplicar metadatos editoriales del DTO sobre el asset y asociarlo a la historia
            IntStream.range(0, request.getMediaFiles() == null ? 0 : request.getMediaFiles().size())
                .forEach(i -> {
                    var dto = request.getMediaFiles().get(i);

                    StoryMediaAsset asset = assetMap.get(dto.getMediaAssetId());
                    if (asset == null) {
                        throw new IllegalStateException(
                            "StoryMediaAsset no encontrado para mediaAssetId: " + dto.getMediaAssetId()
                        );
                    }

                    asset.setMimeType(SupportedMimeType.fromValue(r2Service.detectRealMimeType("public/" + asset.getObjectKey())));
                    // Enriquecer el asset con los metadatos que definió el admin
                    asset.setFileName(dto.getFileName());
                    asset.setAltText(dto.getAltText());
                    asset.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : i);
                    asset.setIsCover(Boolean.TRUE.equals(dto.getIsCover()));
                    asset.setStatus(StoryMediaAsset.MediaAssetStatus.VALIDATED);

                    // Si el publicUrl aún no estaba seteado, construirlo desde el objectKey
                    if (asset.getPublicUrl() == null) {
                        asset.setPublicUrl(buildPublicUrl(asset.getObjectKey()));
                    }

                    story.addMedia(asset);
                });

            ImpactStory saved = storyRepository.save(story);
            log.info("Historia de impacto creada id={}, assets={}", saved.getId(), assetIds);
            return mapper.toResponse(saved);

        } catch (Exception e) {
            log.error("Error creando historia, marcando assets como huérfanos: {}", assetIds, e);
            mediaAssetService.markOrphaned(assetIds);
            throw e;
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public Page<ImpactStoryResponseDTO> findAllForConsumer(Pageable pageable) {
        return storyRepository.findByStatus(StoryStatus.PUBLISHED, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ImpactStoryResponseDTO> findAll(Pageable pageable) {
        return storyRepository.findByStatusNot(StoryStatus.DELETED, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ImpactStoryResponseDTO> findByStatus(StoryStatus status, Pageable pageable) {
        return storyRepository.findByStatus(status, pageable).map(mapper::toResponse);
    }
    
    @Transactional(readOnly = true)
    @Override
    public ImpactStoryResponseDTO findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @Override
    public ImpactStoryResponseDTO update(Long id, UpdateImpactStoryRequestDTO request) {
        ImpactStory story = getOrThrow(id);
        mapper.updateEntity(story, request);
        return mapper.toResponse(storyRepository.save(story));
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    @Override
    public void delete(Long id) {
        ImpactStory story = getOrThrow(id);
        story.getMediaFiles().forEach(media -> mediaAssetService.markOrphaned(List.of(media.getId())));
        story.setStatus(StoryStatus.DELETED);
        storyRepository.save(story);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ImpactStory getOrThrow(Long id) {
        return storyRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("ImpactStory not found: " + id));
    }

    private String buildPublicUrl(String objectKey) {
        return "https://cdn.verygana.com/public/" + objectKey;

    }
}