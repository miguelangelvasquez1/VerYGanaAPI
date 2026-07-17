package com.verygana2.services.surveys;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.survey.AvailableSurveyDTO;
import com.verygana2.dtos.survey.CreateQuestionRequest;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.StartSurveyResponse;
import com.verygana2.dtos.survey.SurveyDetailDTO;
import com.verygana2.dtos.survey.SurveyAdminDetailDTO;
import com.verygana2.dtos.survey.SurveyCommercialDetailDTO;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
import com.verygana2.dtos.survey.UpdateSurveyRequest;
import com.verygana2.dtos.survey.submission.AnswerRequest;
import com.verygana2.dtos.survey.submission.SubmissionResult;
import com.verygana2.dtos.survey.submission.SubmitSurveyRequest;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.exceptions.surveys.SurveyAlreadyCompletedException;
import com.verygana2.exceptions.surveys.SurveyNotActiveException;
import com.verygana2.exceptions.surveys.SurveyNotFoundException;
import com.verygana2.exceptions.surveys.SurveySuspendedException;
import com.verygana2.mappers.SurveyMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.surveys.QuestionOption;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyAnswer;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.models.surveys.SurveySession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;
import com.verygana2.services.PricingConfigService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyService {

    private static final int SESSION_EXPIRY_HOURS = 2;

    private final SurveyRepository surveyRepository;
    private final SurveySessionRepository sessionRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final ConsumerDetailsRepository userDetailsRepository;
    private final WalletRepository walletRepository;
    private final SurveyMapper mapper;
    private final RewardService rewardService;
    private final PricingConfigService pricingConfigService;
    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;

    @Transactional
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_USE_SURVEYS, RequirePlanCapability.Capability.MAX_SURVEYS})
    public SurveyResponseDTO createSurvey(CreateSurveyRequest request, Long commercialId) {

        long minPricePerQuestion = pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY_REWARD_PER_QUESTION_CENTS);
        long pricePerQuestionCents = request.getPricePerQuestionCents();

        if (pricePerQuestionCents < minPricePerQuestion) {
            throw new ValidationException(String.format(
                    "El precio por pregunta (%d ¢) es menor al mínimo permitido (%d ¢)",
                    pricePerQuestionCents, minPricePerQuestion));
        }

        Survey survey = mapper.fromCreateRequest(request);
        attachQuestions(request, survey);

        List<Category> selectedCategories = categoryService.getValidatedCategories(request.getCategoryIds());

        List<Municipality> municipalities = Collections.emptyList();
        if (request.getMunicipalityCodes() != null && !request.getMunicipalityCodes().isEmpty()) {
            municipalities = targetingValidator.getValidatedMunicipalities(request.getMunicipalityCodes());
        }

        int questionCount = request.getQuestions().size();
        long totalBudgetCents = pricePerQuestionCents * questionCount * request.getMaxResponses().longValue();

        Wallet wallet = walletRepository.findByCommercialId(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet del anunciante no encontrado"));

        wallet.consume(totalBudgetCents);
        walletRepository.save(wallet);

        TargetAudience targetAudience = TargetAudience.builder()
                .categories(selectedCategories)
                .targetMunicipalities(municipalities)
                .minAge(request.getMinAge())
                .maxAge(request.getMaxAge())
                .targetGender(request.getTargetGender())
                .build();
        survey.setTargetAudience(targetAudience);
        survey.setStatus(Survey.SurveyStatus.DRAFT);
        survey.setRewardAmountPerQuestionCents(pricePerQuestionCents);
        survey.setCreator(commercialDetailsRepository.findByUser_Id(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Comercial no encontrado: " + commercialId)));

        Survey saved = surveyRepository.save(survey);
        log.info("Survey {} created for commercial {}. Budget deducted: {} ¢",
                saved.getId(), commercialId, totalBudgetCents);
        return mapper.toResponse(saved);
    }

    @Transactional
    public SurveyCommercialDetailDTO updateSurvey(Long surveyId, UpdateSurveyRequest request, Long commercialId) {
        Survey survey = findSurveyOrThrow(surveyId);

        if (survey.getCreator() == null || !survey.getCreator().getId().equals(commercialId)) {
            throw new AccessDeniedException("No tienes permiso para editar esta encuesta");
        }

        if (request.getTitle() != null)       survey.setTitle(request.getTitle());
        if (request.getDescription() != null)  survey.setDescription(request.getDescription());

        TargetAudience ta = survey.getTargetAudience();
        if (ta == null) {
            ta = new TargetAudience();
            survey.setTargetAudience(ta);
        }
        if (request.getMinAge() != null)       ta.setMinAge(request.getMinAge());
        if (request.getMaxAge() != null)       ta.setMaxAge(request.getMaxAge());
        if (request.getTargetGender() != null) ta.setTargetGender(request.getTargetGender());

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            ta.setCategories(categoryService.getValidatedCategories(request.getCategoryIds()));
        }
        if (request.getMunicipalityCodes() != null) {
            ta.setTargetMunicipalities(request.getMunicipalityCodes().isEmpty()
                    ? Collections.emptyList()
                    : targetingValidator.getValidatedMunicipalities(request.getMunicipalityCodes()));
        }

        Survey saved = surveyRepository.save(survey);
        return buildCommercialDTO(saved);
    }

    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_USE_SURVEYS})
    public PagedResponse<SurveySummaryResponse> getAllSurveysForCommercial(
            Pageable pageable, Long commercialId, Survey.SurveyStatus status) {
        Page<Survey> page = status != null
                ? surveyRepository.findAllByCreatorIdAndStatusOrderByCreatedAtDesc(pageable, commercialId, status)
                : surveyRepository.findAllByCreatorIdOrderByCreatedAtDesc(pageable, commercialId);
        return PagedResponse.from(page.map(mapper::toSummaryResponse));
    }

    public SurveyCommercialDetailDTO getSurveyCommercialDetail(Long surveyId, Long commercialId) {
        Survey survey = findSurveyOrThrow(surveyId);

        if (survey.getCreator() == null || !survey.getCreator().getId().equals(commercialId)) {
            throw new AccessDeniedException("No tienes acceso a esta encuesta");
        }

        return buildCommercialDTO(survey);
    }

    private SurveyCommercialDetailDTO buildCommercialDTO(Survey survey) {
        int questionCount = survey.getQuestions().size();
        Long totalBudgetCents = survey.getMaxResponses() != null
                ? (long) questionCount * survey.getMaxResponses() * survey.getRewardAmountPerQuestionCents()
                : null;

        SurveyCommercialDetailDTO dto = mapper.toCommercialDetail(survey);
        dto.setTotalBudgetCents(totalBudgetCents);
        return dto;
    }

    @Transactional
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_USE_SURVEYS})
    public SurveyResponseDTO publishSurvey(Long surveyId, Long commercialId) {
        Survey survey = findSurveyOrThrow(surveyId);
        if (survey.getStatus() == Survey.SurveyStatus.SUSPENDED) {
            throw new SurveySuspendedException(surveyId);
        }
        survey.setStatus(Survey.SurveyStatus.ACTIVE);
        activateIfFirstTime(survey);
        return mapper.toResponse(surveyRepository.save(survey));
    }

    // ─── ADMIN ────────────────────────────────────────────────────────────────

    public PagedResponse<SurveySummaryResponse> getAllSurveys(Pageable pageable, Survey.SurveyStatus status) {
        Page<Survey> page = status != null
                ? surveyRepository.findAllByStatusOrderByCreatedAtDesc(pageable, status)
                : surveyRepository.findAll(pageable);
        return PagedResponse.from(page.map(mapper::toSummaryResponse));
    }

    public SurveyAdminDetailDTO getSurveyAdminDetail(Long surveyId) {
        Survey survey = findSurveyOrThrow(surveyId);
        int questionCount = survey.getQuestions().size();

        long activeSessions    = sessionRepository.countBySurveyIdAndStatus(surveyId, SurveySession.SessionStatus.ACTIVE);
        long completedSessions = sessionRepository.countBySurveyIdAndStatus(surveyId, SurveySession.SessionStatus.COMPLETED);
        long expiredSessions   = sessionRepository.countBySurveyIdAndStatus(surveyId, SurveySession.SessionStatus.EXPIRED);
        long abandonedSessions = sessionRepository.countBySurveyIdAndStatus(surveyId, SurveySession.SessionStatus.ABANDONED);
        long totalSessions     = activeSessions + completedSessions + expiredSessions + abandonedSessions;

        long spentCents = completedSessions * questionCount * survey.getRewardAmountPerQuestionCents();
        Long totalBudgetCents = survey.getMaxResponses() != null
                ? (long) questionCount * survey.getMaxResponses() * survey.getRewardAmountPerQuestionCents()
                : null;

        double completionRate = 0.0;
        if (survey.getMaxResponses() != null && survey.getMaxResponses() > 0) {
            completionRate = Math.round(completedSessions * 1000.0 / survey.getMaxResponses()) / 10.0;
        } else if (totalSessions > 0) {
            completionRate = Math.round(completedSessions * 1000.0 / totalSessions) / 10.0;
        }

        SurveyAdminDetailDTO dto = mapper.toAdminDetail(survey);
        dto.setTotalSessions(totalSessions);
        dto.setActiveSessions(activeSessions);
        dto.setCompletedSessions(completedSessions);
        dto.setExpiredSessions(expiredSessions);
        dto.setAbandonedSessions(abandonedSessions);
        dto.setTotalBudgetCents(totalBudgetCents);
        dto.setSpentCents(spentCents);
        dto.setCompletionRate(completionRate);
        return dto;
    }

    private static final Set<Survey.SurveyStatus> ADMIN_ALLOWED_STATUSES =
            EnumSet.of(Survey.SurveyStatus.CLOSED, Survey.SurveyStatus.SUSPENDED, Survey.SurveyStatus.PAUSED);

    /**
     * Admin-only: restricted to CLOSED ("cancelled"), SUSPENDED, and PAUSED.
     * PAUSED is only allowed to revert a SUSPENDED survey ("un-suspend") — it's not a general
     * admin pause tool, that stays with {@link #updateSurveyStatusAsCommercial}.
     * CLOSED is terminal: once closed, no further admin transition is allowed.
     * No ownership check.
     */
    @Transactional
    public SurveyResponseDTO updateSurveyStatus(Long surveyId, Survey.SurveyStatus status) {
        if (!ADMIN_ALLOWED_STATUSES.contains(status)) {
            throw new ValidationException("Los administradores solo pueden cambiar el estado a CLOSED, SUSPENDED o PAUSED (para revertir una suspensión)");
        }

        Survey survey = findSurveyOrThrow(surveyId);

        if (survey.getStatus() == Survey.SurveyStatus.CLOSED) {
            throw new ValidationException("La encuesta ya fue cerrada y no se puede revertir");
        }
        if (status == Survey.SurveyStatus.PAUSED && survey.getStatus() != Survey.SurveyStatus.SUSPENDED) {
            throw new ValidationException("Los administradores solo pueden poner PAUSED al revertir una encuesta SUSPENDED");
        }

        survey.setStatus(status);
        return mapper.toResponse(surveyRepository.save(survey));
    }

    private static final Set<Survey.SurveyStatus> COMMERCIAL_ALLOWED_STATUSES =
            EnumSet.of(Survey.SurveyStatus.ACTIVE, Survey.SurveyStatus.PAUSED, Survey.SurveyStatus.CLOSED);

    /**
     * Commercial-only status transition, restricted to ACTIVE / PAUSED / CLOSED ("cancelled").
     * DRAFT surveys must go through {@link #publishSurvey} first; CLOSED is terminal.
     */
    @Transactional
    public SurveyResponseDTO updateSurveyStatusAsCommercial(Long surveyId, Survey.SurveyStatus status, Long commercialId) {
        Survey survey = findSurveyOrThrow(surveyId);

        if (survey.getCreator() == null || !survey.getCreator().getId().equals(commercialId)) {
            throw new AccessDeniedException("No tienes permiso para modificar esta encuesta");
        }
        if (!COMMERCIAL_ALLOWED_STATUSES.contains(status)) {
            throw new ValidationException("Los comerciales solo pueden cambiar el estado entre ACTIVE, PAUSED o CLOSED");
        }
        if (survey.getStatus() == Survey.SurveyStatus.DRAFT) {
            throw new ValidationException("La encuesta debe publicarse antes de cambiar su estado");
        }
        if (survey.getStatus() == Survey.SurveyStatus.CLOSED) {
            throw new ValidationException("La encuesta ya fue cerrada y no se puede reactivar");
        }
        if (survey.getStatus() == Survey.SurveyStatus.COMPLETED) {
            throw new ValidationException("La encuesta ya completó su cupo de respuestas y no se puede modificar");
        }
        if (survey.getStatus() == Survey.SurveyStatus.SUSPENDED) {
            throw new AccessDeniedException("La encuesta fue suspendida por un administrador y solo un administrador puede modificarla");
        }

        survey.setStatus(status);
        activateIfFirstTime(survey);
        return mapper.toResponse(surveyRepository.save(survey));
    }

    /** Sets startsAt the first time a survey becomes ACTIVE; never overwritten on later re-activations. */
    private void activateIfFirstTime(Survey survey) {
        if (survey.getStatus() == Survey.SurveyStatus.ACTIVE && survey.getStartsAt() == null) {
            survey.setStartsAt(ZonedDateTime.now());
        }
    }

    // ─── CONSUMER ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<AvailableSurveyDTO> getSurveysForUser(Long userId, Pageable pageable) {
        ConsumerDetails user = userDetailsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Integer age    = user.getAge();
        String  gender = user.getGender() != null ? user.getGender().name() : null;

        Page<AvailableSurveyDTO> page = surveyRepository
                .findActiveSurveysRankedForUser(userId, age, gender, LocalDateTime.now(), pageable)
                .map(mapper::toAvailableSurvey);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public SurveyDetailDTO getSurveyDetail(Long surveyId) {
        return mapper.toSurveyDetail(findSurveyOrThrow(surveyId));
    }

    // ─── Consumer: start / resume session ────────────────────────────────────

    @Transactional
    public StartSurveyResponse startSurvey(Long surveyId, Long consumerId) {
        ZonedDateTime now = ZonedDateTime.now();

        // Pessimistic lock prevents concurrent quota overflow
        Survey survey = surveyRepository.findByIdForUpdate(surveyId)
                .orElseThrow(() -> new SurveyNotFoundException(surveyId));

        // Suspended by an admin: blocks starting AND resuming sessions
        if (survey.getStatus() == Survey.SurveyStatus.SUSPENDED) {
            throw new SurveySuspendedException(surveyId);
        }

        // Resume existing active non-expired session
        Optional<SurveySession> existing = sessionRepository.findActiveNonExpired(surveyId, consumerId, now);
        if (existing.isPresent()) {
            log.debug("Resuming session {} for consumer {} survey {}", existing.get().getId(), consumerId, surveyId);
            return buildStartResponse(existing.get());
        }

        // Survey must be active
        if (survey.getStatus() != Survey.SurveyStatus.ACTIVE) {
            throw new SurveyNotActiveException(surveyId);
        }
        if (survey.getStartsAt() != null && now.isBefore(survey.getStartsAt())) {
            throw new ValidationException("La encuesta aún no ha comenzado");
        }
        if (survey.getEndsAt() != null && now.isAfter(survey.getEndsAt())) {
            throw new ValidationException("La encuesta ya ha finalizado");
        }

        // User must not have already completed
        if (sessionRepository.existsBySurveyIdAndConsumerIdAndStatus(
                surveyId, consumerId, SurveySession.SessionStatus.COMPLETED)) {
            throw new SurveyAlreadyCompletedException();
        }

        // Quota check: completed responses + active non-expired sessions
        if (survey.getMaxResponses() != null) {
            long activeSessions = sessionRepository.countActiveNonExpiredBySurveyId(surveyId, now);
            if (survey.getResponseCount() + activeSessions >= survey.getMaxResponses()) {
                throw new ValidationException("La encuesta no tiene cupos disponibles");
            }
        }

        // Lazily expire any stale ACTIVE session for this user on this survey
        sessionRepository.findActiveBySurveyAndConsumer(surveyId, consumerId)
                .ifPresent(stale -> {
                    stale.setStatus(SurveySession.SessionStatus.EXPIRED);
                    sessionRepository.save(stale);
                });

        ConsumerDetails consumer = userDetailsRepository.findById(consumerId)
                .orElseThrow(() -> new EntityNotFoundException("Consumer not found: " + consumerId));

        SurveySession session = SurveySession.builder()
                .survey(survey)
                .consumer(consumer)
                .expiresAt(now.plusHours(SESSION_EXPIRY_HOURS))
                .build();

        return buildStartResponse(sessionRepository.save(session));
    }

    // ─── Consumer: submit answers ─────────────────────────────────────────────

    @Transactional
    public SubmissionResult submitSurvey(SubmitSurveyRequest request, Long consumerId) {

        SurveySession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + request.getSessionId()));

        if (!session.getConsumer().getId().equals(consumerId)) {
            throw new IllegalArgumentException("La sesión no pertenece a este usuario");
        }
        if (session.getStatus() != SurveySession.SessionStatus.ACTIVE) {
            throw new IllegalStateException("La sesión no está activa: " + session.getStatus());
        }
        if (session.isExpiredByTime()) {
            session.setStatus(SurveySession.SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new IllegalStateException("La sesión ha expirado. Inicia una nueva.");
        }

        Survey survey = session.getSurvey();

        if (survey.getStatus() == Survey.SurveyStatus.SUSPENDED) {
            throw new SurveySuspendedException(survey.getId());
        }

        Map<Long, SurveyQuestion> questionMap = survey.getQuestions().stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));

        List<Long> requiredIds = survey.getQuestions().stream()
                .filter(SurveyQuestion::getRequired)
                .map(SurveyQuestion::getId)
                .toList();

        List<Long> answeredIds = request.getAnswers().stream()
                .map(AnswerRequest::getQuestionId)
                .toList();

        List<Long> missing = requiredIds.stream()
                .filter(id -> !answeredIds.contains(id))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Preguntas obligatorias sin responder: " + missing);
        }

        List<SurveyAnswer> answers = request.getAnswers().stream()
                .map(ar -> buildAnswer(ar, session, questionMap))
                .collect(Collectors.toList());

        session.getAnswers().addAll(answers);
        session.setStatus(SurveySession.SessionStatus.COMPLETED);
        session.setCompletedAt(ZonedDateTime.now());

        SurveySession savedSession = sessionRepository.save(session);
        surveyRepository.incrementResponseCount(survey.getId());
        surveyRepository.completeIfQuotaReached(survey.getId(), Survey.SurveyStatus.COMPLETED,
                List.of(Survey.SurveyStatus.ACTIVE, Survey.SurveyStatus.PAUSED));

        SurveyReward reward = rewardService.grantReward(savedSession);

        return SubmissionResult.builder()
                .sessionId(savedSession.getId())
                .status(savedSession.getStatus())
                .reward(mapper.toRewardInfo(reward))
                .message("¡Gracias por completar la encuesta! Tu recompensa ha sido acreditada.")
                .build();
    }

    // ─── Consumer: rewards summary ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserRewardsSummary getUserRewardsSummary(Long userId) {
        return rewardService.getUserRewardsSummary(userId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private StartSurveyResponse buildStartResponse(SurveySession session) {
        return StartSurveyResponse.builder()
                .sessionId(session.getId())
                .expiresAt(session.getExpiresAt())
                .survey(mapper.toSurveySessionDTO(session.getSurvey()))
                .build();
    }

    private Survey findSurveyOrThrow(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new SurveyNotFoundException(id));
    }

    private void attachQuestions(CreateSurveyRequest request, Survey survey) {
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            return;
        }

        List<SurveyQuestion> questions = new ArrayList<>();

        for (int qi = 0; qi < request.getQuestions().size(); qi++) {
            CreateQuestionRequest qr = request.getQuestions().get(qi);

            SurveyQuestion question = SurveyQuestion.builder()
                    .survey(survey)
                    .text(qr.getText())
                    .type(qr.getType())
                    .required(Boolean.TRUE.equals(qr.getRequired()))
                    .orderIndex(qi)
                    .build();

            if (qr.getOptions() != null && !qr.getOptions().isEmpty()) {
                List<QuestionOption> options = new ArrayList<>();
                for (int oi = 0; oi < qr.getOptions().size(); oi++) {
                    options.add(QuestionOption.builder()
                            .question(question)
                            .text(qr.getOptions().get(oi))
                            .orderIndex(oi)
                            .build());
                }
                question.setOptions(options);
            }
            questions.add(question);
        }
        survey.setQuestions(questions);
    }

    private SurveyAnswer buildAnswer(AnswerRequest ar, SurveySession session,
                                     Map<Long, SurveyQuestion> questionMap) {
        SurveyQuestion question = questionMap.get(ar.getQuestionId());
        if (question == null) {
            throw new IllegalArgumentException("Pregunta no encontrada: " + ar.getQuestionId());
        }

        SurveyAnswer.SurveyAnswerBuilder builder = SurveyAnswer.builder()
                .session(session)
                .question(question);

        switch (question.getType()) {
            case TEXT, RATING, YES_NO -> builder.textAnswer(ar.getTextAnswer());
            case SINGLE_CHOICE -> {
                if (ar.getSelectedOptionId() != null) {
                    QuestionOption opt = question.getOptions().stream()
                            .filter(o -> o.getId().equals(ar.getSelectedOptionId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Opción no encontrada: " + ar.getSelectedOptionId()));
                    builder.selectedOption(opt);
                }
            }
            case MULTIPLE_CHOICE -> {
                if (ar.getSelectedOptionIds() != null) {
                    Map<Long, QuestionOption> optMap = question.getOptions().stream()
                            .collect(Collectors.toMap(QuestionOption::getId, Function.identity()));
                    List<QuestionOption> selected = ar.getSelectedOptionIds().stream()
                            .map(id -> {
                                QuestionOption o = optMap.get(id);
                                if (o == null) throw new IllegalArgumentException("Opción no encontrada: " + id);
                                return o;
                            }).toList();
                    builder.selectedOptions(selected);
                }
            }
        }
        return builder.build();
    }
}
