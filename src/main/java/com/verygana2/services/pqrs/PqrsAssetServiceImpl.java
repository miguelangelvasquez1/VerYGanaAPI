package com.verygana2.services.pqrs;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.pqrs.PqrsAssetProperties;
import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.pqrs.requests.PreparePqrsAssetRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.exceptions.pqrsExceptions.PqrsAccessDeniedException;
import com.verygana2.mappers.pqrs.PqrsAssetMapper;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;
import com.verygana2.repositories.pqrs.PqrsAssetRepository;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.storage.service.R2Service;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PqrsAssetServiceImpl implements PqrsAssetService {

    private static final Set<SupportedMimeType> ALLOWED_MIME_TYPES = Set.of(
            SupportedMimeType.IMAGE_PNG, SupportedMimeType.IMAGE_JPEG, SupportedMimeType.IMAGE_JPG,
            SupportedMimeType.IMAGE_WEBP, SupportedMimeType.VIDEO_MP4, SupportedMimeType.VIDEO_QUICK_TIME);
    private static final String OBJECT_KEY_PREFIX = "pqrs-evidence/";

    private final PqrsAssetRepository pqrsAssetRepository;
    private final PqrsAssetMapper pqrsAssetMapper;
    private final PqrsAssetProperties pqrsAssetProperties;
    private final R2Service r2Service;

    @Override
    public PqrsAssetUploadPermissionDTO prepareUpload(Long ownerUserId, PreparePqrsAssetRequestDTO dto) {
        if (dto.getSizeBytes() > pqrsAssetProperties.getMaxSizeBytes()) {
            throw new ValidationException(
                    "Archivo muy grande. Máximo permitido: " + (pqrsAssetProperties.getMaxSizeBytes() / 1024 / 1024) + " MB");
        }

        SupportedMimeType declaredMime;
        try {
            declaredMime = SupportedMimeType.fromValue(dto.getContentType());
        } catch (ValidationException e) {
            throw new ValidationException("Tipo de archivo no soportado. Use JPG, PNG, WEBP o video MP4/MOV.");
        }
        if (!ALLOWED_MIME_TYPES.contains(declaredMime)) {
            throw new ValidationException("Tipo de archivo no soportado. Use JPG, PNG, WEBP o video MP4/MOV.");
        }

        String objectKey = OBJECT_KEY_PREFIX + ownerUserId + "/" + UUID.randomUUID();

        PqrsAsset asset = PqrsAsset.builder()
                .ownerUserId(ownerUserId)
                .objectKey(objectKey)
                .originalFileName(dto.getOriginalFileName())
                .sizeBytes(dto.getSizeBytes())
                .status(PqrsAssetStatus.PENDING)
                .build();
        PqrsAsset saved = pqrsAssetRepository.save(asset);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, dto.getContentType());

        return new PqrsAssetUploadPermissionDTO(saved.getId(), permission);
    }

    @Override
    public PqrsAssetResponseDTO confirmUpload(Long ownerUserId, Long assetId) {
        PqrsAsset asset = getOwnedAssetOrThrow(assetId, ownerUserId);

        if (asset.getStatus() != PqrsAssetStatus.PENDING) {
            throw new ValidationException("El archivo ya fue confirmado o no está pendiente de subida");
        }

        SupportedMimeType realMime = r2Service.validateUploadedObject(
                true, asset.getObjectKey(), asset.getSizeBytes(), pqrsAssetProperties.getMaxSizeBytes(), ALLOWED_MIME_TYPES);

        asset.setMimeType(realMime);
        asset.setMediaType(realMime.getMediaType());
        asset.setStatus(PqrsAssetStatus.VALIDATED);
        pqrsAssetRepository.save(asset);

        log.info("[PQRS-ASSET] {} confirmado por ownerUserId={} ({})", asset.getId(), ownerUserId, realMime.getMime());

        return pqrsAssetMapper.toResponseDTO(asset);
    }

    @Override
    public List<PqrsAsset> validateAndClaimAssets(List<Long> assetIds, Long ownerUserId, Pqrs pqrs) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }

        if (assetIds.size() > pqrsAssetProperties.getMaxCount()) {
            throw new ValidationException(
                    "Máximo " + pqrsAssetProperties.getMaxCount() + " archivos de evidencia por PQRS");
        }

        List<PqrsAsset> assets = pqrsAssetRepository.findAllByIdIn(assetIds);
        if (assets.size() != assetIds.size()) {
            throw new ValidationException("Uno o más archivos de evidencia no existen");
        }

        for (PqrsAsset asset : assets) {
            if (!asset.getOwnerUserId().equals(ownerUserId)) {
                throw new PqrsAccessDeniedException("El archivo " + asset.getId() + " no pertenece a este usuario");
            }
            if (asset.getStatus() != PqrsAssetStatus.VALIDATED) {
                throw new ValidationException("El archivo " + asset.getId() + " todavía no fue confirmado");
            }
            if (asset.getPqrs() != null) {
                throw new ValidationException("El archivo " + asset.getId() + " ya está adjunto a otro PQRS");
            }
        }

        for (PqrsAsset asset : assets) {
            asset.setPqrs(pqrs);
        }
        pqrsAssetRepository.saveAll(assets);

        return assets;
    }

    @Override
    @Transactional(readOnly = true)
    public void streamAsset(Long assetId, Long callerUserId, boolean isAdmin, HttpServletResponse response) throws IOException {
        PqrsAsset asset = pqrsAssetRepository.findById(assetId)
                .orElseThrow(() -> new ObjectNotFoundException("Archivo de evidencia no encontrado: " + assetId, PqrsAsset.class));

        // Mismo criterio que ProductServiceImpl.streamPrivateProductImage: el
        // dueño siempre puede ver lo suyo; cualquier admin puede ver cualquier
        // evidencia (no se restringe al admin asignado por rotación — ídem
        // producto pendiente, que cualquier admin puede revisar).
        if (!isAdmin && !asset.getOwnerUserId().equals(callerUserId)) {
            throw new PqrsAccessDeniedException("Este archivo no pertenece a este usuario");
        }

        log.info("[PQRS-ASSET] Streaming {} solicitado por userId={} (admin={})", assetId, callerUserId, isAdmin);

        try (var stream = r2Service.getPrivateObjectStream(asset.getObjectKey())) {
            String contentType = stream.response().contentType();
            response.setContentType(contentType != null ? contentType
                    : (asset.getMimeType() != null ? asset.getMimeType().getMime() : "application/octet-stream"));
            Long contentLength = stream.response().contentLength();
            if (contentLength != null && contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            response.setHeader("Cache-Control", "private, max-age=300");
            stream.transferTo(response.getOutputStream());
        }
    }

    private PqrsAsset getOwnedAssetOrThrow(Long assetId, Long ownerUserId) {
        PqrsAsset asset = pqrsAssetRepository.findById(assetId)
                .orElseThrow(() -> new ObjectNotFoundException("Archivo de evidencia no encontrado: " + assetId, PqrsAsset.class));

        if (!asset.getOwnerUserId().equals(ownerUserId)) {
            throw new PqrsAccessDeniedException("Este archivo no pertenece a este usuario");
        }
        return asset;
    }
}
