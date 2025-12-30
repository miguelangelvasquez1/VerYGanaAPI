package com.verygana2.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CreateAssetRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.mappers.GameMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.games.Asset;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameAssetDefinition;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameAssetDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.interfaces.CampaignService;
import com.verygana2.storage.service.AssetOrphanedService;
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

    private final AssetOrphanedService assetOrphanedService;
    private final CampaignRepository campaignRepository;
    private final GameRepository gameRepository;
    private final GameAssetDefinitionRepository assetDefinitionRepository;
    private final AssetRepository assetRepository;
    private final GameMapper mapper;
    private final R2Service r2Service;

    // public PagedResponse<CampaignDTO> getAdvertiserCapaings(Long advertiserId) {
    //     return campaignRepository.
    // }

    /**
     * PASO 1: Validar estructura y generar pre-signed URLs 
     */
    @Override
    public List<AssetUploadPermissionDTO> prepareAssetUploads( //dejar bien lo de crear anuncio, ver lista de auncios
            Long gameId,
            Long advertiserId,
            List<CreateAssetRequestDTO> assetRequests) {

        log.info("Preparando URLs de subida para {} assets del juego {}", assetRequests.size(), gameId);

        // 1. Validar que el juego existe
        Game game = gameRepository.findById(Objects.requireNonNull(gameId, "gameId must not be null"))
            .orElseThrow(() -> new EntityNotFoundException("Juego no encontrado: " + gameId));

        // 2. Validar que el advertiser no tenga una campaña con ese juego
        boolean campaignAlreadyExists = campaignRepository.existsByAdvertiserIdAndGameId(advertiserId, gameId);
        if (campaignAlreadyExists) {
            throw new ValidationException("El usuario ya tiene una campaña con ese juego");
        }

        // 3. Validar estructura de assets según reglas del juego
        validateAssetStructure(game, assetRequests);

        // 4. Generar pre-signed URLs para cada asset
        List<AssetUploadPermissionDTO> uploadPermissions = new ArrayList<>();

        for (CreateAssetRequestDTO assetReq : assetRequests) {
            GameAssetDefinition definition = assetDefinitionRepository
                .findById(Objects.requireNonNull(assetReq.getAssetDefinitionId()))
                .orElseThrow(() -> new ValidationException("Asset definition no válida: " + assetReq.getAssetDefinitionId()));

            // Validar tipo de archivo vs tipo permitido
            validateFileMetadata(definition, assetReq.getFileMetadata());

            // Generar key única en R2
            String objectKey = generateObjectKey(gameId, definition, assetReq);

            // Crear asset
            Asset asset = Asset.builder()
                .objectKey(objectKey)
                .sizeBytes(assetReq.getFileMetadata().getSizeBytes())
                .assetType(definition.getAssetType())
                .mediaType(definition.getMediaType())
                .assetDefinition(definition)
                .build();
            
            Asset createdAsset = assetRepository.save(Objects.requireNonNull(asset));

            // Obtener pre-signed URL de R2
            FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
                objectKey,
                assetReq.getFileMetadata().getContentType());

            uploadPermissions.add(new AssetUploadPermissionDTO(createdAsset.getId(), permission));
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
    @Override
    public Campaign createCampaignWithAssets(
            Long gameId,
            Long advertiserId,
            List<Long> assetIds) {

        log.info("Creando campaña para juego {} con {} assets", gameId, assetIds.size());

        List<Asset> assets = List.of();
        try {
            // 1. Validar entidades
            Game game = gameRepository.findById(Objects.requireNonNull(gameId))
                .orElseThrow(() -> new EntityNotFoundException("Juego no encontrado: " + gameId));

            AdvertiserDetails advertiser = entityManager.getReference(AdvertiserDetails.class, advertiserId);

            // 2. Validar assetsIds
            assets = assetRepository.findAllByIdInAndStatus(assetIds, AssetStatus.PENDING);

            if (assets.size() != assetIds.size()) {                
                throw new ValidationException("Uno o más assets no existen");
            }

            // 3. Validaciones de dominio + R2
            for (Asset asset: assets) {

                if (asset.getCampaign() != null) {
                    throw new ValidationException("Asset ya está asociado a una campaña: " + asset.getId());
                }

                GameAssetDefinition definition = asset.getAssetDefinition();

                if (!definition.getGame().getId().equals(gameId)) {
                    throw new ValidationException("Asset no pertenece a este juego");
                }

                long maxSizeBytes = definition.getMaxSizeBytes();
                Set<SupportedMimeType> allowedMimeType = definition.getAllowedMimeTypes();

                SupportedMimeType realMime = r2Service.validateUploadedObject(asset.getObjectKey(), asset.getSizeBytes(), maxSizeBytes, allowedMimeType);
            
                asset.setMimeType(realMime);
                asset.setStatus(AssetStatus.VALIDATED);
            }

            // 4. Validar que se cumplan los requerimientos del juego
            validateCampaignAssets(game, assets);

            // 5. Crear campaña (aún no persistida)
            Campaign campaign = new Campaign();
            campaign.setGame(game);
            campaign.setAdvertiser(advertiser);
            campaign.setStatus(CampaignStatus.ACTIVE);
            campaign.setAssets(new ArrayList<>());

            // 6. Asociar assets a la campaña
            for (Asset asset : assets) {
                asset.setCampaign(campaign);
                campaign.getAssets().add(asset);
            }

            // 7. Persistir
            Campaign savedCampaign = campaignRepository.save(campaign);

            log.info("Campaña creada exitosamente: ID {}, {} assets", 
                savedCampaign.getId(), savedCampaign.getAssets().size());
            // if (true) throw new ValidationException("Excepción de prueba");

            return savedCampaign;

        } catch (Exception e) {
            // Si algo falla, marcar assets como huérfanos para limpieza posterior
            log.error("Error creando campaña, marcando assets como huérfanos");
            if (!assets.isEmpty()) assetOrphanedService.markAssetsAsOrphanedByIds(assets.stream().map(Asset::getId).toList());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<GameDTO> getAvailableGames(Long advertiserId, Pageable pageable) {
        return PagedResponse.from(gameRepository.findGamesWithoutCampaign(advertiserId, pageable));
    }

    @Transactional(readOnly = true)
    @Override
    public List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId) {
        List<GameAssetDefinition> defs = assetDefinitionRepository.findByGameIdWithMimeTypes(gameId);
        return mapper.toDtoList(defs);
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
     * Valida que los assets de la campaña cumplan los requerimientos de la definición
     */
    private void validateCampaignAssets(Game game, List<Asset> assets) {
        List<GameAssetDefinition> definitions = assetDefinitionRepository.findByGameId(game.getId());

        // Verificar que todos los assets requeridos estén presentes
        for (GameAssetDefinition def : definitions) {
            if (def.isRequired()) {
                boolean found = assets.stream()
                    .anyMatch(asset -> asset.getAssetDefinition().getId().equals(def.getId()));

                if (!found) {
                    throw new ValidationException("Campaña incompleta: falta asset " + def.getAssetType());
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
        
        // Validar contra AssetDefinition
        definition.validateMimeType(metadata.getContentType());

        if (metadata.getSizeBytes() > definition.getMaxSizeBytes()) {
            throw new ValidationException(
                String.format(
                    "Archivo %s demasiado grande. Máximo: %.2f MB.",
                    metadata.getOriginalFileName(),
                    definition.getMaxSizeBytes() / (1024.0 * 1024.0)
                )
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
     * Extrae extensión del nombre de archivo
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}