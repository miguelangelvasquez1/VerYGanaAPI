package com.verygana2.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CreateAssetRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.games.Asset;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameAssetDefinition;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameAssetDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.interfaces.CampaignService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CampaignServiceImpl implements CampaignService {
    
    @PersistenceContext
    private EntityManager entityManager;

    private final CampaignRepository campaignRepository;
    private final GameRepository gameRepository;
    private final GameAssetDefinitionRepository assetDefinitionRepository;
    private final R2Service r2Service;

    /**
     * PASO 1: Validar estructura y generar pre-signed URLs
     * 
     * Este método NO crea nada en BD, solo valida y prepara URLs de subida
     */
    @Transactional(readOnly = true)
    @Override
    public Map<Long, AssetUploadPermissionDTO> prepareAssetUploads(
            Long gameId,
            List<CreateAssetRequestDTO> assetRequests) {

        log.info("Preparando URLs de subida para {} assets del juego {}", 
            assetRequests.size(), gameId);

        // 1. Validar que el juego existe
        java.util.Objects.requireNonNull(gameId, "gameId must not be null");
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new EntityNotFoundException("Juego no encontrado: " + gameId));

        // 2. Validar estructura de assets según reglas del juego
        validateAssetStructure(game, assetRequests);

        // 3. Generar pre-signed URLs para cada asset
        Map<Long, AssetUploadPermissionDTO> uploadPermissions = new HashMap<>();

        for (CreateAssetRequestDTO assetReq : assetRequests) {
            GameAssetDefinition definition = assetDefinitionRepository
                .findById(java.util.Objects.requireNonNull(assetReq.getAssetDefinitionId(), "assetDefinition must not be null"))
                .orElseThrow(() -> new ValidationException(
                    "Asset definition no válida: " + assetReq.getAssetDefinitionId()
                ));

            // Validar tipo de archivo vs tipo permitido
            validateFileMetadata(definition, assetReq.getFileMetadata());

            // Generar key única en R2
            String objectKey = generateObjectKey(gameId, definition, assetReq);

            // Obtener pre-signed URL de R2
            AssetUploadPermissionDTO permission = r2Service.generateUploadUrl(
                objectKey,
                assetReq.getFileMetadata().getContentType(),
                3600L // 1 hora para completar upload
            );

            uploadPermissions.put(assetReq.getAssetDefinitionId(), permission);
        }

        log.info("URLs de subida generadas exitosamente para {} assets", uploadPermissions.size());
        return uploadPermissions;
    }

    /**
     * PASO 2: Crear campaña con assets ya subidos
     * 
     * Este método SOLO se llama después de que el frontend confirme
     * que todos los uploads a R2 fueron exitosos
     * 
     * @param uploadedAssets Map de assetDefinitionId -> objectKey en R2
     */
    @Transactional
    @Override
    public Campaign createCampaignWithAssets(
            Long gameId,
            Long advertiserId,
            Map<Long, String> uploadedAssets) {

        log.info("Creando campaña para juego {} con {} assets", gameId, uploadedAssets.size());

        try {
            // 1. Validar entidades
            java.util.Objects.requireNonNull(gameId, "gameId must not be null");
            Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Juego no encontrado: " + gameId));

            AdvertiserDetails advertiser = entityManager.getReference(AdvertiserDetails.class, advertiserId);

            // 2. Verificar que todos los assets existen en R2
            List<String> missingAssets = new ArrayList<>();
            for (Map.Entry<Long, String> entry : uploadedAssets.entrySet()) {
                String objectKey = entry.getValue();
                if (!r2Service.objectExists(objectKey)) {
                    missingAssets.add(objectKey);
                }
            }

            if (!missingAssets.isEmpty()) {
                log.error("Assets faltantes en R2: {}", missingAssets);
                throw new ValidationException(
                    "Assets no encontrados en storage: " + missingAssets.size()
                );
            }

            // 3. Crear campaña
            Campaign campaign = new Campaign();
            campaign.setGame(game);
            campaign.setAdvertiser(advertiser);
            campaign.setActive(true);
            campaign.setAssets(new ArrayList<>());

            // 4. Crear entidades Asset vinculadas
            for (Map.Entry<Long, String> entry : uploadedAssets.entrySet()) {
                Long defId = entry.getKey();
                String objectKey = entry.getValue();

                GameAssetDefinition definition = assetDefinitionRepository.findById(
                    java.util.Objects.requireNonNull(defId, "defId must not be null"))
                    .orElseThrow(() -> new EntityNotFoundException(
                        "Asset definition no encontrada: " + defId
                    ));

                // Verificar que el definition pertenece al juego correcto
                if (!definition.getGame().getId().equals(gameId)) {
                    throw new ValidationException(
                        "Asset definition no pertenece al juego especificado"
                    );
                }

                Asset asset = new Asset();
                asset.setCampaign(campaign);
                asset.setAssetDefinition(definition);
                asset.setAssetType(definition.getAssetType());
                asset.setMediaType(definition.getMediaType());
                asset.setContent(r2Service.buildPublicUrl(objectKey)); // URL pública CDN

                campaign.getAssets().add(asset);
            }

            // 5. Validar que se cumplan los requerimientos del juego
            validateCampaignAssets(game, campaign.getAssets());

            // 6. Guardar campaña
            Campaign savedCampaign = campaignRepository.save(campaign);

            log.info("Campaña creada exitosamente: ID {}, {} assets", 
                savedCampaign.getId(), savedCampaign.getAssets().size());

            return savedCampaign;

        } catch (Exception e) {
            // Si algo falla, marcar assets como huérfanos para limpieza posterior
            log.error("Error creando campaña, marcando assets como huérfanos", e);
            markAssetsAsOrphans(uploadedAssets.values());
            throw e;
        }
    }

    /**
     * Marca assets como huérfanos cuando falla la creación de campaña
     */
    private void markAssetsAsOrphans(Collection<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                r2Service.markAsOrphan(objectKey);
            } catch (Exception e) {
                log.warn("No se pudo marcar {} como huérfano: {}", objectKey, e.getMessage());
            }
        }
    }

    /**
     * Validar que los assets cumplen las reglas del juego
     */
    private void validateAssetStructure(Game game, List<CreateAssetRequestDTO> assetRequests) {
        List<GameAssetDefinition> definitions = assetDefinitionRepository.findByGameId(game.getId());
        
        // Verificar assets requeridos
        for (GameAssetDefinition def : definitions) {
            if (def.isRequired()) {
                boolean found = assetRequests.stream()
                    .anyMatch(req -> req.getAssetDefinitionId().equals(def.getId()));
                
                if (!found) {
                    throw new ValidationException(
                        String.format("Asset requerido faltante: %s (%s)",
                            def.getAssetType(),
                            def.getDescription())
                    );
                }
            }
        }
        
        // Verificar múltiples no permitidos
        Map<Long, Long> counts = assetRequests.stream()
            .collect(Collectors.groupingBy(
                CreateAssetRequestDTO::getAssetDefinitionId,
                Collectors.counting()
            ));
        
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            GameAssetDefinition def = definitions.stream()
                .filter(d -> d.getId().equals(entry.getKey()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                    "Asset definition inválida: " + entry.getKey()
                ));
            
            if (!def.isMultiple() && entry.getValue() > 1) {
                throw new ValidationException(
                    "No se permiten múltiples assets para: " + def.getAssetType()
                );
            }
        }
    }

     /**
     * Valida que los assets de la campaña cumplan los requerimientos
     */
    private void validateCampaignAssets(Game game, List<Asset> assets) {
        List<GameAssetDefinition> definitions = assetDefinitionRepository.findByGameId(game.getId());

        // Verificar que todos los assets requeridos estén presentes
        for (GameAssetDefinition def : definitions) {
            if (def.isRequired()) {
                boolean found = assets.stream()
                    .anyMatch(asset -> asset.getAssetDefinition().getId().equals(def.getId()));

                if (!found) {
                    throw new ValidationException(
                        "Campaña incompleta: falta asset " + def.getAssetType()
                    );
                }
            }
        }
    }

    /**
     * Validar metadata del archivo
     */
    private void validateFileMetadata(
            GameAssetDefinition definition, 
            FileUploadRequestDTO metadata) {
        
        if (metadata == null) {
            throw new ValidationException("Metadata de archivo requerida");
        }
        
        // Validar contentType contra mediaType permitido
        MediaType mediaType = definition.getMediaType();
        String contentType = metadata.getContentType();
        
        if (!isContentTypeValid(mediaType, contentType)) {
            throw new ValidationException(
                String.format("ContentType %s no válido para %s", 
                    contentType, mediaType)
            );
        }
        
        // Validar tamaño según tipo de media
        long maxSize = getMaxSizeForMediaType(mediaType);
        if (metadata.getSizeBytes() > maxSize) {
            throw new ValidationException(
                String.format("Archivo demasiado grande: %d bytes. Máximo: %d bytes",
                    metadata.getSizeBytes(),
                    maxSize)
            );
        }

        // Validar nombre de archivo
        if (metadata.getOriginalFileName() == null || 
            metadata.getOriginalFileName().trim().isEmpty()) {
            throw new ValidationException("Nombre de archivo requerido");
        }
    }

    private String generateObjectKey(
            Long gameId, 
            GameAssetDefinition definition,
            CreateAssetRequestDTO request) {
        
        String uuid = UUID.randomUUID().toString();
        String extension = getFileExtension(request.getFileMetadata().getOriginalFileName());
        
        return String.format("campaigns/game_%d/%s/%s.%s",
            gameId,
            definition.getAssetType().name().toLowerCase(),
            uuid,
            extension
        );
    }

    /**
     * Verifica si el contentType es válido para el mediaType
     */
    private boolean isContentTypeValid(MediaType mediaType, String contentType) {
        if (contentType == null) return false;

        return switch (mediaType) {
            case IMAGE -> contentType.startsWith("image/") && 
                         (contentType.contains("jpeg") || 
                          contentType.contains("jpg") ||
                          contentType.contains("png") ||
                          contentType.contains("gif") ||
                          contentType.contains("webp"));
            case VIDEO -> contentType.startsWith("video/") &&
                         (contentType.contains("mp4") ||
                          contentType.contains("webm") ||
                          contentType.contains("quicktime"));
            case AUDIO -> contentType.startsWith("audio/");
            default -> false;
        };
    }

    /**
     * Obtiene tamaño máximo permitido según tipo de media
     */
    private long getMaxSizeForMediaType(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> 10 * 1024 * 1024L;  // 10 MB
            case VIDEO -> 100 * 1024 * 1024L; // 100 MB
            case AUDIO -> 20 * 1024 * 1024L;  // 20 MB
            default -> 5 * 1024 * 1024L;      // 5 MB default
        };
    }

    /**
     * Extrae extensión del nombre de archivo
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    @Transactional(readOnly = true)
    @Override
    public List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId) {
        return assetDefinitionRepository.findDtosByGameId(gameId);
    }
}
