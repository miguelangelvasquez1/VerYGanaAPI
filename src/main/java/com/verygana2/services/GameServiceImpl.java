package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.mappers.GameMapper;
import com.verygana2.models.enums.DevicePlatform;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GameServiceImpl implements GameService {

    @Value("${games.session-expiration-minutes}")
    private Integer sessionExpirationTime;

    @PersistenceContext
    private EntityManager entityManager;

    private final GameRepository gameRepository;
    private final CampaignRepository campaignRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameMapper gameMapper;
    private final GameMetricDefinitionRepository metricDefinitionRepository;
    private final MetricValidator metricValidator;
    private final GameSessionMetricRepository gameSessionMetricRepository;
    
    @Override
    public InitGameResponseDTO initGameSponsored(InitGameRequestDTO request, Long userId) {
        
        ConsumerDetails consumer = entityManager.getReference(ConsumerDetails.class, userId);

        // 2. Validar juego
        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
            .orElseThrow(() -> new IllegalArgumentException("Game not available"));

        // 3. Seleccionar campaña válida para el juego
        Campaign campaign = campaignRepository.findRandomActiveCampaignByGameId(gameId)
            .orElseThrow(() -> new IllegalStateException("No active campaigns for this game"));

        // 4. Crear sesión
        GameSession session = GameSession.start(consumer, game, resolvePlatform());

        // 5. Persistir
        GameSession savedSession = gameSessionRepository.save(
            java.util.Objects.requireNonNull(session, "session must not be null"));

        // 6. Construir respuesta
        return gameMapper.toInitResponse(
            savedSession,
            game,
            campaign
        );
    }

    @Override
    public InitGameResponseDTO initGameNotSponsored(InitGameRequestDTO request, Long userId) {

        ConsumerDetails consumer = entityManager.getReference(ConsumerDetails.class, userId);

        // 2. Validar juego
        Long gameId = java.util.Objects.requireNonNull(request.getGameId(), "gameId must not be null");

        Game game = gameRepository.findByIdAndActiveTrue(gameId)
            .orElseThrow(() -> new IllegalArgumentException("Game not available"));

        // 3. Crear sesión
        GameSession session = GameSession.start(consumer, game, resolvePlatform());

        // 4. Persistir
        GameSession savedSession = gameSessionRepository.save(
            java.util.Objects.requireNonNull(session, "session must not be null"));

        // 5. Construir respuesta
        return gameMapper.toInitResponse(
            savedSession,
            game,
            null // No campaign for non-sponsored games
        );
    }

    /**
     * Recibe y guarda métricas de una sesión de juego
     */
    @Override
    public void submitGameMetrics(GameEventDTO<List<GameMetricDTO>> event, Long userId) {
        // Validar sesión y permisos
        GameSession session = validateSessionOwnership(event.getSessionToken(), event.getUserHash(), userId);

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

        GameSession session = validateSessionOwnership(event.getSessionToken(), event.getUserHash(), userId);

        ZonedDateTime end = ZonedDateTime.now();

        session.setCompleted(true);
        session.setEndTime(end);
        session.setPlayTime(
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

    private DevicePlatform resolvePlatform() {
        // ejemplo simple
        return DevicePlatform.MOBILE; // 1 = web, 2 = android, 3 = ios
    }

    private GameSession validateSessionOwnership(String sessionToken, String userHash, Long userId) {

        GameSession session = gameSessionRepository.findBySessionToken(
            java.util.Objects.requireNonNull(sessionToken, "sessionToken must not be null"))
            .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        if (!session.getUserHash().equals(userHash)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        if (!session.getConsumer().getId().equals(userId)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

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
        metric.setMetricValue(dto.getValue());
        // metric.setUnit(dto.getUnit());
        metric.setRecordedAt(ZonedDateTime.now());
        return metric;
    }
}
