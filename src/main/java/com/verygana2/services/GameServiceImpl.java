package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.enums.DevicePlatform;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.games.GameMetricDefinition;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.games.GameSessionMetric;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameMetricDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.repositories.games.GameSessionMetricRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.services.interfaces.GameService;
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
    private final GameSessionRepository gameSessionRepository;
    private final GameMetricDefinitionRepository metricDefinitionRepository;
    private final MetricValidator metricValidator;
    private final GameSessionMetricRepository gameSessionMetricRepository;
    
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
                configDefinition.getUiSchema()
        );
    }

    @Override
    public String initGameSponsored(InitGameRequestDTO request, Long userId) {
         
        ConsumerDetails consumer = entityManager.getReference(ConsumerDetails.class, userId);

        // 2. Validar juego
        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
            .orElseThrow(() -> new ValidationException("Game not available"));

        // 3. Seleccionar campaña válida para el juego
        Campaign campaign = campaignRepository.findRandomActiveCampaignByGameId(gameId)
            .orElseThrow(() -> new ValidationException("No active campaigns for this game"));

        // 4. Crear sesión
        GameSession session = GameSession.start(consumer, game, resolvePlatform(), campaign);

        // 5. Persistir
        GameSession savedSession = gameSessionRepository.save(java.util.Objects.requireNonNull(session, "session must not be null"));

        String sessionToken = savedSession.getSessionToken();
        String userHash = savedSession.getUserHash();
        String isBrandedMode = "true";
        Long campaignId = campaign.getId();
        
        String baseUrl = generateGameUrl(game);
        
        //Para pruebas
        // String testUrl = String.format(
        //     "http://localhost:63035/?game_title=%s&session_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
        //     game.getUrl(), sessionToken, userHash, isBrandedMode, campaignId
        // );

        return String.format(
            "%ssession_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
            baseUrl, sessionToken, userHash, isBrandedMode, campaignId
        );
    }

    @Override
    public String initGameNotSponsored(InitGameRequestDTO request, Long userId) {

        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
            .orElseThrow(() -> new ValidationException("Game not available"));

        String baseUrl = generateGameUrl(game);

        return String.format(
            "%ssession_token=%s&user_hash=%s&is_branded_mode=%s&campaign_id=%s",
            baseUrl, "public", userId.toString(), "false", "none"
        );
    }

    @Transactional(readOnly = true)
    public Map<String,Object> getGameAssets(GameEventDTO<Void> req) {
        if (req.getCampaignId() == null) {
            throw new ObjectNotFoundException("Campaign ID is required", Campaign.class);
        }

        Campaign campaign = campaignRepository.findById(req.getCampaignId())
            .orElseThrow(() ->
                new ObjectNotFoundException(
                    "Campaign not found with id: " + req.getCampaignId(), Campaign.class
                )
            );
        return campaign.getConfigData();
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
            java.time.Duration.between(session.getStartTime(), end).getSeconds()
        );
        session.setScore(event.getPayload().getFinalScore());

        gameSessionRepository.save(session);
    }

    @Override
    public PagedResponse<GameDTO> getAvailableGamesPage (Pageable pageable) {
        Page<GameDTO> page = gameRepository.findAvailableGames(pageable);
        return PagedResponse.from(page);
    }

    //Métodos privados auxiliares

    private String generateGameUrl(Game game) {
        String baseUrl;

        if (game.getDeliveryType() == Game.DeliveryType.PATH) {

            // Para justudios
            baseUrl = String.format("https://%s/%s/build/?",
                "justudios.co/test-verygana",
                game.getUrl()
            );

            // baseUrl = String.format("https://%s/%s/%s/%s/?",
            //     cdnUrl,
            //     "builds/build-bogota",
            //     "28-04-2026", // Cambia segun version
            //     game.getUrl()
            // );
        } else if (game.getDeliveryType() == Game.DeliveryType.QUERY) {

            baseUrl = String.format("https://%s/%s/?game_title=%s&",
                cdnUrl,
                "builds/build-cali",
                game.getUrl()
            );
        } else {
            throw new ValidationException("Unsupported routing type");
        }
        return baseUrl;
    }

    private DevicePlatform resolvePlatform() {
        // ejemplo simple
        return DevicePlatform.MOBILE; // 1 = web, 2 = android, 3 = ios
    }

    private GameSession validateSessionOwnership(String sessionToken, String userHash) {

        GameSession session = gameSessionRepository.findBySessionToken(
            java.util.Objects.requireNonNull(sessionToken, "sessionToken must not be null"))
            .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        if (!session.getUserHash().equals(userHash)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        // if (!session.getConsumer().getId().equals(userId)) {
        //     throw new UnauthorizedException("Session does not belong to user");
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
}
