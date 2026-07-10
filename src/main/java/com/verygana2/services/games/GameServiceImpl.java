package com.verygana2.services.games;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.RewardCardResponseDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.Category;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.DevicePlatform;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.games.GameMetricDefinition;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.games.GameSessionMetric;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameMetricDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.repositories.games.GameSessionMetricRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.interfaces.GameService;
import com.verygana2.services.scoring.ScoringContext;
import com.verygana2.utils.validators.MetricValidator;

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
public class GameServiceImpl implements GameService {

    @Value("${cloudflare.r2.games-domain}")
    private String cdnUrl;

    @Value("${games.session-expiration-minutes}")
    private Integer sessionExpirationTime;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;
    private final GameRepository gameRepository;
    private final CampaignRepository campaignRepository;
    private final BrandingRequestRepository brandingRequestRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameMetricDefinitionRepository metricDefinitionRepository;
    private final MetricValidator metricValidator;
    private final GameSessionMetricRepository gameSessionMetricRepository;
    private final ProductRepository productRepository;
    private final CampaignScorer campaignScorer;
    private final CampaignScoringConfig campaignScoringConfig;

    public GameSchemaResponse getLatestGameSchema(Long gameId) {

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
                .orElseThrow(() -> new ValidationException("Game not available"));

        GameConfigDefinition configDefinition = game.getConfigDefinitions()
                .stream()
                .max(Comparator.comparing(GameConfigDefinition::getVersion))
                .orElseThrow(() -> new ValidationException("Game has no config definition"));

        return new GameSchemaResponse(
                game.getId(),
                game.getTitle(),
                configDefinition.getVersion().toString(),
                configDefinition.getJsonSchema(),
                configDefinition.getUiSchema());
    }

    @Override
    public String initGameSponsored(InitGameRequestDTO request, Long userId) {

        ConsumerDetails consumer = entityManager.getReference(ConsumerDetails.class, userId);

        // 2. Validar juego
        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
                .orElseThrow(() -> new ValidationException("Game not available"));

        // 3. Seleccionar la campaña más adecuada para el juego y el consumidor
        Campaign campaign = selectBestCampaign(gameId, consumer)
                .orElseThrow(() -> new ValidationException("No active campaigns for this game"));

        // 4. Crear sesión
        GameSession session = GameSession.start(consumer, game, resolvePlatform(), campaign);

        // 5. Persistir
        GameSession savedSession = gameSessionRepository
                .save(java.util.Objects.requireNonNull(session, "session must not be null"));

        String sessionToken = savedSession.getSessionToken();
        String userHash = savedSession.getUserHash();
        String isBrandedMode = "true";
        Long campaignId = campaign.getId();

        String baseUrl = generateGameUrl(game);

        // Para pruebas
        // String testUrl = String.format(
        // "http://localhost:63035/?game_title=%s&session_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
        // game.getUrl(), sessionToken, userHash, isBrandedMode, campaignId
        // );

        return String.format(
                "%ssession_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
                baseUrl, sessionToken, userHash, isBrandedMode, campaignId);
    }

    @Override
    public String initGameNotSponsored(InitGameRequestDTO request, Long userId) {

        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
                .orElseThrow(() -> new ValidationException("Game not available"));

        String baseUrl = generateGameUrl(game);

        return String.format(
                "%ssession_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
                baseUrl, "public", userId.toString(), "false", "none");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGameAssets(GameEventDTO<Void> req) {
        if (req.getCampaignId() == null) {
            throw new ObjectNotFoundException("Campaign ID is required", Campaign.class);
        }

        Campaign campaign = campaignRepository.findById(req.getCampaignId())
                .orElseThrow(() -> new ObjectNotFoundException("Campaign not found with id: " + req.getCampaignId(), Campaign.class));
        
        Map<String, Object> assets = new java.util.HashMap<>(campaign.getConfigData());

        List<RewardCardResponseDTO> rewards = getGameRewards(campaign);

        Map<String, Object> rewardPopup = Map.of(
                "popup_title", "Recompensas desbloqueadas",
                "products", rewards
        );
        assets.put("reward_popup", rewardPopup);
    
        return assets;
    }

    /**
     * Recibe y guarda métricas de una sesión de juego
     */
    @Override
    public void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event) {
        // Validar sesión y permisos
        GameSession session = validateSessionOwnership(event.getSessionToken(), event.getUserHash());

        // Obtener definiciones de métricas del juego
        List<GameMetricDefinition> definitions = metricDefinitionRepository
                .findByGameId(session.getGame().getId());

        List<GameMetricDTO> metrics = event.getPayload();

        // Validar métricas contra definiciones
        metricValidator.validateMetrics(metrics, definitions);

        // Convertir y guardar métricas
        List<GameSessionMetric> sessionMetrics = metrics.stream()
                .map(dto -> buildSessionMetric(dto, session))
                .collect(Collectors.toList());

        gameSessionMetricRepository.saveAll(java.util.Objects.requireNonNull(
                sessionMetrics, "sessionMetrics must not be null"));

        // Devolver los puntos ganados por el usuario

        log.info("Submitted {} metrics for session {}", metrics.size(), event.getSessionToken());
    }

    @Override
    public void completeSession(GameEventDTO<EndSessionDTO> event, Long userId) {

        GameSession session = validateSessionOwnership(event.getSessionToken(), event.getUserHash());

        ZonedDateTime end = ZonedDateTime.now();

        session.setCompleted(true);
        session.setEndTime(end);
        session.setPlayTimeSeconds(
                java.time.Duration.between(session.getStartTime(), end).getSeconds());
        session.setScore(event.getPayload().getFinalScore());

        gameSessionRepository.save(session);
    }

    @Override
    public PagedResponse<GameDTO> getAvailableGamesPage(Pageable pageable) {
        Page<GameDTO> page = gameRepository.findAvailableGames(pageable);
        return PagedResponse.from(page);
    }

    // ===== PREVIEW =====

    @Override
    public String generatePreviewUrl(BrandingRequest brandingRequest) {
        String baseUrl = generateGameUrl(brandingRequest.getGame());
        return String.format(
            "%ssession_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
            baseUrl, "preview", "preview", "true", brandingRequest.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPreviewAssets(Long brandingRequestId) {
        BrandingRequest request = brandingRequestRepository.findById(brandingRequestId)
            .orElseThrow(() -> new EntityNotFoundException("Preview not found for id: " + brandingRequestId));

        Map<String, Object> draft = request.getDraftFormData();
        if (draft == null || draft.isEmpty()) return Map.of();

        return stripPreviewMap(draft);
    }

    private Map<String, Object> stripPreviewMap(Map<String, Object> map) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> result.put(k, stripPreviewValue(v)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object stripPreviewValue(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            if (map.containsKey("assetId") && map.containsKey("url")) return map.get("url");
            return stripPreviewMap(map);
        }
        if (value instanceof List<?> list) return list.stream().map(this::stripPreviewValue).toList();
        return value;
    }

    // Métodos privados auxiliares

    private String generateGameUrl(Game game) {
        String baseUrl;

        if (game.getDeliveryType() == Game.DeliveryType.PATH) {

            // Para justudios
        //     baseUrl = String.format("https://%s/%s/build/?",
        //             "justudios.co/test-verygana",
        //             game.getUrl());

            baseUrl = String.format("https://%s/%s/%s/?",
            cdnUrl,
            "builds/build-bogota",
            //"28-04-2026", // Cambia segun version
            game.getUrl()
            );
        } else if (game.getDeliveryType() == Game.DeliveryType.QUERY) {

            baseUrl = String.format("https://%s/%s/?game_title=%s&",
                    cdnUrl,
                    "builds/build-cali",
                    game.getUrl());
        } else {
            throw new ValidationException("Unsupported routing type");
        }
        return baseUrl;
    }

    private DevicePlatform resolvePlatform() {
        // ejemplo simple
        return DevicePlatform.MOBILE; // 1 = web, 2 = android, 3 = ios
    }

    /**
     * Selecciona la campaña más adecuada para el juego y el consumidor dados, usando el mismo
     * enfoque de dos etapas que el sistema de anuncios: hard filters en el repositorio
     * (elegibilidad) + scoring ponderado en {@link CampaignScorer} (preferencia).
     */
    private Optional<Campaign> selectBestCampaign(Long gameId, ConsumerDetails consumer) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(now.getZone());

        List<Campaign> candidates = campaignRepository.findEligibleCampaignsForConsumer(
                gameId,
                consumer.getId(),
                CampaignStatus.ACTIVE,
                consumer.getMunicipality(),
                todayStart,
                PageRequest.of(0, campaignScoringConfig.getCandidateLimit()));

        if (candidates.isEmpty()) return Optional.empty();

        Set<Long> candidateIds = candidates.stream().map(Campaign::getId).collect(Collectors.toSet());
        Map<Long, ZonedDateTime> lastPlayedAt = gameSessionRepository
                .findLastPlayedAtByCampaignIds(consumer.getId(), candidateIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (ZonedDateTime) row[1]));

        ScoringContext ctx = new ScoringContext(
                consumer.getId(),
                consumer.getAge(),
                toTargetGender(consumer.getGender()),
                consumer.getCategories().stream().map(Category::getId).collect(Collectors.toSet()),
                lastPlayedAt,
                now);

        return campaignScorer.selectBest(candidates, ctx);
    }

    private TargetGender toTargetGender(Gender gender) {
        if (gender == Gender.MALE) return TargetGender.MALE;
        if (gender == Gender.FEMALE) return TargetGender.FEMALE;
        return null;
    }

    private GameSession validateSessionOwnership(String sessionToken, String userHash) {

        GameSession session = gameSessionRepository.findBySessionToken(
                java.util.Objects.requireNonNull(sessionToken, "sessionToken must not be null"))
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        if (!session.getUserHash().equals(userHash)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        // if (!session.getConsumer().getId().equals(userId)) {
        // throw new UnauthorizedException("Session does not belong to user");
        // }

        if (session.getStartTime().plusMinutes(sessionExpirationTime).isBefore(ZonedDateTime.now())) {
            throw new BusinessException("Session expired");
        }

        if (session.isCompleted()) {
            throw new BusinessException("Cannot submit metrics for completed session");
        }

        return session;
    }

    private GameSessionMetric buildSessionMetric(GameMetricDTO dto, GameSession session) {
        GameSessionMetric metric = new GameSessionMetric();
        metric.setSession(session);
        metric.setMetricKey(dto.getKey());
        metric.setMetricType(dto.getType());
        metric.setMetricValue(toJsonNode(dto.getValue()));
        // metric.setUnit(dto.getUnit());
        metric.setRecordedAt(ZonedDateTime.now());
        return metric;
    }

    private JsonNode toJsonNode(Object value) {
        return value == null
                ? objectMapper.nullNode()
                : objectMapper.valueToTree(value);
    }

    private List<RewardCardResponseDTO> getGameRewards(Campaign campaign) {

        List<Product> gameRewards = productRepository
                .findGameRewardsProducts(campaign.getCommercial().getId());

        if (gameRewards.isEmpty()) {
            return List.of();
        }

        return gameRewards.stream()
                .map(p -> RewardCardResponseDTO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .image_url(p.getImageUrl())
                        .image_message(p.getMaxKeysPct() + "% Descuento")
                        .regular_price(p.getPriceCents() / 100)
                        .keys_message("Con [[" + formatNumber(p.getMaxKeysAllowed()) + "]] llaves pagas [[SOLO $"
                                + formatNumber(p.getMinCashCents() / 100) + " COP]]")
                        .commercial(p.getCommercial().getCompanyName())
                        .rating(p.getAverageRate())
                        .max_keys_allowed(p.getMaxKeysAllowed())
                        .min_cash_cents(p.getMinCashCents())
                        .stock(p.getAvailableStock())
                        .category_name(p.getProductCategory().getName())
                        .build())
                .toList();
    }

    private String formatNumber(long number) {
        return String.format("%,d", number).replace(",", ".");
    }
}
