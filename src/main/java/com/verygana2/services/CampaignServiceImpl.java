package com.verygana2.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CreateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO.TargetAudienceDTO;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.CampaignMapper;
import com.verygana2.mappers.GameMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameAssetDefinition;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameAssetDefinitionRepository;
import com.verygana2.repositories.games.GameConfigDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.interfaces.CampaignService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.utils.validators.TargetingValidator;

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

    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;
    private final CampaignMapper campaignMapper;
    private final CampaignRepository campaignRepository;
    private final GameRepository gameRepository;
    private final GameAssetDefinitionRepository assetDefinitionRepository;
    private final GameConfigDefinitionRepository gameConfigDefinitionRepository;
    private final AssetRepository assetRepository;
    private final GameMapper gameMapper;
    private final Clock clock;

    /**
     * Create new campaign with full validation
     */
    @Transactional
    @Override
    public void createCampaign(CreateCampaignRequestDTO request, Long userId) {
        log.info("Creating campaign for game: {}", request.getGameId());
        
        // Load game
        Game game = gameRepository.findById(request.getGameId())
            .orElseThrow(() -> new IllegalArgumentException("Game not found: " + request.getGameId()));
        
        // Load latest config definition
        GameConfigDefinition configDefinition = gameConfigDefinitionRepository
            .findFirstByGameIdOrderByVersionDesc(request.getGameId())
            .orElseThrow(() -> new IllegalArgumentException("No config definition found for game: " + game.getTitle()));

        List<Category> categories = categoryService.getValidatedCategories(request.getCategoryIds());

        List<Municipality> municipalities = Collections.emptyList();
        List<String> municipalityCodes = request.getTargetAudience().getMunicipalityCodes();
        if (municipalityCodes != null && !municipalityCodes.isEmpty()) {
            municipalities = targetingValidator.getValidatedMunicipalities(municipalityCodes);
        }
        
        // Create campaign
        Campaign campaign = Campaign.builder()
            .game(game)
            .configDefinition(configDefinition)
            .configData(request.getConfigData())

            // Estado inicial
            .status(CampaignStatus.DRAFT)

            // Presupuesto y economía
            .budget(request.getBudget())
            .coinValue(request.getCoinValue())
            .completionCoins(request.getCompletionCoins())
            .budgetCoins(request.getBudgetCoins())
            .maxCoinsPerSession(request.getMaxCoinsPerSession())
            .maxSessionsPerUserPerDay(request.getMaxSessionsPerUserPerDay())

            // Configuración de campaña
            .targetUrl(request.getTargetUrl())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())

            // Audiencia
            .targetAudience(TargetAudience.builder()
                .categories(categories)
                .targetMunicipalities(municipalities)
                .targetGender(request.getTargetAudience().getGender())
                .minAge(request.getTargetAudience().getMinAge())
                .maxAge(request.getTargetAudience().getMaxAge())
                .build())

            // Relación con commercial
            .commercial(entityManager.getReference(CommercialDetails.class, userId))
            .build();
        
        campaign = campaignRepository.save(campaign);

        attachAssetsToCampaign(request.getConfigData(), campaign);

        log.info("Campaign created successfully with ID: {}", campaign.getId());
    }

    @Override
    public List<CampaignDTO> getCommercialCampaigns(Long commercialId) {
        List<Campaign> campaigns = campaignRepository.findByCommercialId(commercialId);
        return campaigns.stream().map(campaignMapper::toDto).toList();
    }

    @Override
    public void updateCampaignStatus(Long campaignId, Long userId, CampaignStatus newStatus) {

        Campaign campaign = campaignRepository.findById(Objects.requireNonNull(campaignId))
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        // 1. Autorización
        if (!campaign.getCommercial().getUser().getId().equals(userId)) {
            throw new UnauthorizedActionException("No autorizado para modificar esta campaña");
        }

        CampaignStatus currentStatus = campaign.getStatus();

        // 2. Validar transición
        validateStatusTransition(campaign, currentStatus, newStatus);

        // 3. Aplicar transición
        campaign.setStatus(newStatus);

        // 4. Reglas laterales según estado
        handleSideEffects(campaign, newStatus);

        campaignRepository.save(campaign);
    }

    @Override
    public void updateCampaign(Long campaignId, Long userId, UpdateCampaignRequestDTO request) {

        Campaign campaign = campaignRepository.findById(Objects.requireNonNull(campaignId))
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        // 1. Autorización
        if (!campaign.getCommercial().getUser().getId().equals(userId)) {
            throw new UnauthorizedActionException("No autorizado para modificar esta campaña");
        }

        // 2. Restricciones por estado
        if (campaign.getStatus() == CampaignStatus.CANCELLED ||
            campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new ValidationException("No se puede editar una campaña cancelada o completada");
        }

        // 3. Presupuesto
        if (request.getBudgetCoins() != null && request.getCoinValue() != null) {
            BigDecimal newBudget = BigDecimal.valueOf(request.getBudgetCoins()).multiply(request.getCoinValue());
            validateBudgetChange(campaign, newBudget);
            campaign.setBudget(newBudget);
        }

        // 4. Target URL
        campaign.setTargetUrl(request.getTargetUrl());

        // 5. Audiencia
        applyTargetAudience(campaign, request);

        campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<GameDTO> getAvailableGames(Long commercialId, Pageable pageable) {
        return PagedResponse.from(gameRepository.findGamesWithoutCampaign(commercialId, pageable));
    }

    @Transactional(readOnly = true)
    @Override
    public List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId) {
        List<GameAssetDefinition> defs = assetDefinitionRepository.findByGameIdWithMimeTypes(gameId);
        return gameMapper.toDtoList(defs);
    }

    /**
     * Attach all assets found in config to campaign
     */
    private void attachAssetsToCampaign(Map<String, Object> configData, Campaign campaign) {
        // Extract all asset URLs from config
        Set<String> assetUrls = extractAssetUrls(configData);
        
        if (assetUrls.isEmpty()) {
            return;
        }
        
        log.debug("Attaching {} assets to campaign: {}", assetUrls.size(), campaign.getId());
        
        List<Asset> assets = assetRepository.findByObjectKeyIn(assetUrls);
        
        for (Asset asset : assets) {
            if (asset.getStatus() == AssetStatus.VALIDATED) {
                asset.markAsAttached(campaign);
                assetRepository.save(asset);
                log.debug("Attached asset: {} to campaign: {}", asset.getObjectKey(), campaign.getId());
            }
        }
    }

    /**
     * Extract all asset URLs recursively from config
     */
    private Set<String> extractAssetUrls(Object obj) {
        Set<String> urls = new HashSet<>();
        
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                
                // Check if this is likely an asset URL field
                if (isAssetField(key) && value instanceof String) {
                    String url = (String) value;
                    if (isValidAssetUrl(url)) {
                        urls.add(url);
                    }
                } else {
                    urls.addAll(extractAssetUrls(value));
                }
            }
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (Object item : list) {
                urls.addAll(extractAssetUrls(item));
            }
        }
        
        return urls;
    }

    private boolean isAssetField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("url") || 
               lower.contains("image") || 
               lower.contains("icon") || 
               lower.contains("sprite") || 
               lower.contains("texture") || 
               lower.contains("audio") || 
               lower.contains("sound") || 
               lower.contains("music") ||
               lower.contains("model") ||
               lower.contains("background");
    }
    
    private boolean isValidAssetUrl(String url) {
        // Basic validation - should point to R2 storage
        return url != null && 
               !url.isBlank() && 
               (url.startsWith("https://") || url.startsWith("http://"));
    }

    private void validateStatusTransition(Campaign campaign, CampaignStatus from, CampaignStatus to) {

        if (from == to) {
            throw new ValidationException("La campaña ya está en ese estado");
        }

        switch (from) {
            case DRAFT -> {
                if (to != CampaignStatus.ACTIVE && to != CampaignStatus.PAUSED) {
                    throw new ValidationException("Transición inválida desde DRAFT");
                }
            }
            case ACTIVE -> {
                if (to != CampaignStatus.PAUSED && to != CampaignStatus.CANCELLED) {
                    throw new ValidationException("Transición inválida desde ACTIVE");
                }
            }
            case PAUSED -> {
                if (to != CampaignStatus.ACTIVE && to != CampaignStatus.CANCELLED) {
                    throw new ValidationException("Transición inválida desde PAUSED");
                }
            }
            case CANCELLED -> {
                throw new ValidationException("Una campaña cancelada no puede modificarse");
            }
            case COMPLETED -> {
                throw new ValidationException("Una campaña completada no puede modificarse");
            }
        }

        // Reglas adicionales
        if (to == CampaignStatus.ACTIVE && campaign.getAssets().isEmpty()) {
            throw new ValidationException("No se puede activar una campaña sin assets");
        }
    }

    private void handleSideEffects(Campaign campaign, CampaignStatus newStatus) {

        ZonedDateTime now = ZonedDateTime.now(clock);

        switch (newStatus) {
            case ACTIVE -> {
                if (campaign.getStartDate() == null) {
                    campaign.setStartDate(now);
                }
            }
            case CANCELLED -> {
                if (campaign.getEndDate() == null) {
                    campaign.setEndDate(now);
                }
                for (Asset asset : campaign.getAssets()) {
                    asset.setStatus(AssetStatus.CANCELLED);
                }
            }
            default -> {
            }
        }
    }

    private void validateBudgetChange(Campaign campaign, BigDecimal newBudget) {

        if (newBudget.compareTo(campaign.getSpent()) < 0) {
            throw new ValidationException("El presupuesto no puede ser menor al monto ya gastado");
        }

        BigDecimal additional = newBudget.subtract(campaign.getBudget());
        if (additional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal walletCOP = BigDecimal.valueOf(campaign.getCommercial().getWallet().getBalanceCents())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (additional.compareTo(walletCOP) > 0) {
                throw new ValidationException("Saldo insuficiente para incrementar el presupuesto");
            }
        }
    }

    private void applyTargetAudience(Campaign campaign, UpdateCampaignRequestDTO request) {
        TargetAudienceDTO dto = request.getTargetAudience();

        TargetAudience ta = campaign.getTargetAudience();
        if (ta == null) {
            ta = new TargetAudience();
            campaign.setTargetAudience(ta);
        }

        if (request.getCategoryIds() != null) {
            ta.setCategories(categoryService.getValidatedCategories(request.getCategoryIds()));
        }

        if (dto != null) {
            if (dto.getMinAge() != null && dto.getMaxAge() != null && dto.getMinAge() > dto.getMaxAge()) {
                throw new ValidationException("Rango de edad inválido");
            }
            if (dto.getMinAge() != null) ta.setMinAge(dto.getMinAge());
            if (dto.getMaxAge() != null) ta.setMaxAge(dto.getMaxAge());
            if (dto.getGender() != null) ta.setTargetGender(dto.getGender());
            if (dto.getMunicipalityCodes() != null) {
                ta.setTargetMunicipalities(targetingValidator.getValidatedMunicipalities(dto.getMunicipalityCodes()));
            }
        }
    }
}