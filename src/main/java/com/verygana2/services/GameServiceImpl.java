package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.mappers.GameMapper;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameMetricDefinition;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.games.GameSessionMetric;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.CampaignRepository;
import com.verygana2.repositories.GameMetricDefinitionRepository;
import com.verygana2.repositories.GameRepository;
import com.verygana2.repositories.GameSessionMetricRepository;
import com.verygana2.repositories.GameSessionRepository;
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
    public InitGameResponseDTO initGameSession(InitGameRequestDTO request, Long userId) {
        
        validateNotNullRequests(request, userId);

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
        GameSession savedSession = gameSessionRepository.save(session);

        // 6. Construir respuesta
        return gameMapper.toInitResponse(
            savedSession,
            game,
            campaign
        );
    }

    /**
     * Recibe y guarda métricas de una sesión de juego
     */
    @Transactional
    public void submitGameMetrics(Long sessionId, List<GameMetricDTO> metrics, Long userId) {
        // Validar sesión y permisos
        GameSession session = validateSessionOwnership(sessionId, userId);
        
        // Obtener definiciones de métricas del juego
        List<GameMetricDefinition> definitions = metricDefinitionRepository
            .findByGameId(session.getGame().getId());
        
        // Validar métricas contra definiciones
        metricValidator.validateMetrics(metrics, definitions);
        
        // Convertir y guardar métricas
        List<GameSessionMetric> sessionMetrics = metrics.stream()
            .map(dto -> buildSessionMetric(dto, session))
            .collect(Collectors.toList());
        
        gameSessionMetricRepository.saveAll(sessionMetrics);
        
        log.info("Submitted {} metrics for session {}", metrics.size(), sessionId);
    }


    //Métodos privados auxiliares

    private Long resolvePlatform() {
        // ejemplo simple
        return 1L; // 1 = web, 2 = android, 3 = ios
    }

    private void validateNotNullRequests(InitGameRequestDTO request, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (request.getGameId() == null) {
            throw new IllegalArgumentException("gameId must not be null");
        }
    }

    private GameSession validateSessionOwnership(Long sessionId, Long userId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        if (!session.getConsumer().getId().equals(userId)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        if (session.isCompleted()) {
            throw new BusinessException("Cannot submit metrics for completed session");
        }

        session.setCompleted(true);
        session.setEndTime(ZonedDateTime.now());
        //etc
        gameSessionRepository.save(session);
        
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
