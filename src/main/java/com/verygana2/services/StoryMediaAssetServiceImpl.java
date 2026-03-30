package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.impactStory.PrepareMediaUploadRequestDTO;
import com.verygana2.dtos.impactStory.PrepareMediaUploadResponseDTO;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.services.interfaces.StoryMediaAssetService;
import com.verygana2.storage.service.R2Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryMediaAssetServiceImpl implements StoryMediaAssetService {

    private final StoryMediaAssetRepository assetRepository;
    private final R2Service r2Service;

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;  // 20 MB
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024; // 100 MB

    /**
     * PASO 1: El admin solicita permiso para subir un archivo al CDN.
     *
     * - Valida el content-type y tamaño
     * - Crea un StoryMediaAsset en estado PENDING
     * - Genera una pre-signed URL en R2
     * - Devuelve el mediaAssetId y la URL
     */
    @Transactional
    @Override
    public PrepareMediaUploadResponseDTO prepareUpload(PrepareMediaUploadRequestDTO request) {

        SupportedMimeType mimeType = SupportedMimeType.fromValue(request.getContentType());

        // 2. Validar tamaño
        validateSize(request.getSizeBytes(), mimeType.getMediaType());

        // 3. Generar object key único en R2
        String objectKey = generateObjectKey(request.getOriginalFileName());

        // 4. Crear asset en PENDING
        StoryMediaAsset asset = StoryMediaAsset.builder()
            .objectKey(objectKey)
            .sizeBytes(request.getSizeBytes())
            .mediaType(mimeType.getMediaType())
            .mimeType(mimeType)
            .status(StoryMediaAsset.MediaAssetStatus.PENDING)
            .build();

        StoryMediaAsset saved = assetRepository.save(asset);

        // 5. Generar pre-signed URL
        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
            false,
            objectKey,
            request.getContentType()
        );
        return PrepareMediaUploadResponseDTO.builder()
            .mediaAssetId(String.valueOf(saved.getId()))
            .permission(permission)
            .build();
    }

    /**
     * Valida y marca como VALIDATED todos los assets de una lista de IDs.
     * Se llama desde ImpactStoryService durante la creación de la historia.
     */
    @Transactional
    @Override
    public List<StoryMediaAsset> validateAndClaimAssets(List<Long> assetIds) {
        List<StoryMediaAsset> assets = assetRepository.findAllById(assetIds);

        if (assets.size() != assetIds.size()) {
            throw new IllegalArgumentException("Uno o más mediaAssetId no fueron encontrados");
        }

        for (StoryMediaAsset asset : assets) {
            if (asset.getStatus() != StoryMediaAsset.MediaAssetStatus.PENDING) {
                throw new IllegalStateException(
                    "Asset " + asset.getId() + " no está en estado PENDING: " + asset.getStatus()
                );
            }
            if (asset.getImpactStory() != null) {
                throw new IllegalStateException(
                    "Asset " + asset.getId() + " ya está asociado a una historia"
                );
            }
        }

        return assetRepository.saveAll(assets);
    }

    /** Marca assets como huérfanos si el proceso de creación de historia falla */
    @Transactional
    @Override
    public void markOrphaned(List<Long> assetIds) {
        List<StoryMediaAsset> assets = assetRepository.findAllById(assetIds);
        assets.forEach(a -> a.setStatus(StoryMediaAsset.MediaAssetStatus.ORPHANED));
        assetRepository.saveAll(assets);
        log.warn("Assets marcados como huérfanos: {}", assetIds);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private void validateSize(long sizeBytes, MediaType mediaType) {
        long max = mediaType == MediaType.IMAGE ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (sizeBytes > max) {
            throw new IllegalArgumentException(
                String.format("Archivo demasiado grande. Máximo: %d MB", max / (1024 * 1024))
            );
        }
    }

    private String generateObjectKey(String originalFileName) {
        String ext = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            ext = originalFileName.substring(originalFileName.lastIndexOf('.'));
        }
        String year = String.valueOf(ZonedDateTime.now().getYear());
        return String.format("impact-stories/%s/%s%s", year, UUID.randomUUID(), ext);
    }
}