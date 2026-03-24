package com.verygana2.services.surveys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.survey.CreateQuestionRequest;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
import com.verygana2.dtos.survey.submission.AnswerRequest;
import com.verygana2.dtos.survey.submission.SubmissionResult;
import com.verygana2.dtos.survey.submission.SubmitSurveyRequest;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.exceptions.surveys.SurveyAlreadyCompletedException;
import com.verygana2.exceptions.surveys.SurveyNotActiveException;
import com.verygana2.exceptions.surveys.SurveyNotFoundException;
import com.verygana2.mappers.SurveyMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.surveys.QuestionOption;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyAnswer;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.surveys.SurveyResponse;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.CategoryRepository;
import com.verygana2.repositories.MunicipalityRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveyResponseRepository;
import com.verygana2.services.PricingConfigService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyService {
 
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final ConsumerDetailsRepository userDetailsRepository;
    private final SurveyMapper mapper;
    private final RewardService rewardService;
    private final PricingConfigService pricingConfigService;
 
    // ─── Admin: create survey ─────────────────────────────────────────────────
 
    @Transactional
    public SurveyResponseDTO createSurvey(CreateSurveyRequest request, Long userId) {
        Survey survey = mapper.fromCreateRequest(request);

        attachQuestions(request, survey);
 
        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
        survey.setCategories(categories);
 
        if (request.getMunicipalityCodes() != null && !request.getMunicipalityCodes().isEmpty()) {
            List<Municipality> municipalities =
                municipalityRepository.findAllById(request.getMunicipalityCodes());
            survey.setTargetMunicipalities(municipalities);
        }
 
        survey.setStatus(Survey.SurveyStatus.DRAFT);
        survey.setRewardAmount(pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY));
        survey.setCreator(commercialDetailsRepository.findByUser_Id(userId)
            .orElseThrow(() -> new EntityNotFoundException("Commercial details not found for user: " + userId)));
        Survey saved = surveyRepository.save(survey);
        log.info("Survey created with id={}", saved.getId());
        return mapper.toResponse(saved);
    }

    public PagedResponse<SurveySummaryResponse> getAllSurveys(Pageable pageable) {
        Page<SurveySummaryResponse> page = surveyRepository.findAll(pageable)
            .map(mapper::toSummaryResponse);
        return PagedResponse.from(page);
    }

    public SurveyResponseDTO getSurveyDetailForAdmin(Long surveyId) {
        Survey survey = findSurveyOrThrow(surveyId);
        return mapper.toResponse(survey);
    }
 
    @Transactional
    public SurveyResponseDTO publishSurvey(Long surveyId) {
        Survey survey = findSurveyOrThrow(surveyId);
        survey.setStatus(Survey.SurveyStatus.ACTIVE);
        return mapper.toResponse(surveyRepository.save(survey));
    }
 
    @Transactional
    public SurveyResponseDTO updateSurveyStatus(Long surveyId, Survey.SurveyStatus status) {
        Survey survey = findSurveyOrThrow(surveyId);
        survey.setStatus(status);
        return mapper.toResponse(surveyRepository.save(survey));
    }
 
    // ─── User: get ranked surveys ─────────────────────────────────────────────
 
    /**
     * Returns surveys ordered by how many of the user's profile attributes
     * match the survey's targeting criteria. The native query assigns a
     * match_score (0-4) per survey.
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getSurveysForUser(Long userId, Pageable pageable) {
        ConsumerDetails user = userDetailsRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
 
            // REVISAR ESTO DE LA EDAD
        Integer age = user.getAge();
        String gender = user.getGender() != null ? user.getGender().name() : null;
 
        log.info("user age: {}", age);
        return surveyRepository
            .findActiveSurveysRankedForUser(userId, age, gender, LocalDateTime.now(), pageable)
            .map(mapper::toSummaryResponse);
    }
 
    // ─── User: get survey detail ──────────────────────────────────────────────
 
    @Transactional(readOnly = true)
    public SurveyResponseDTO getSurveyDetail(Long surveyId, Long userId) {
        Survey survey = findSurveyOrThrow(surveyId);
        validateUserCanStartSurvey(surveyId, userId, survey);
        return mapper.toResponse(survey);
    }
 
    // ─── User: submit answers ─────────────────────────────────────────────────
 
    @Transactional
    public SubmissionResult submitSurvey(SubmitSurveyRequest request, Long userId) {
 
        Long surveyId = request.getSurveyId();
        Survey survey = findSurveyOrThrow(surveyId);
        validateUserCanStartSurvey(surveyId, userId, survey);
 
        // Fetch or create in-progress response
        SurveyResponse surveyResponse = responseRepository
            .findBySurveyIdAndUserId(surveyId, userId)
            .orElseGet(() -> SurveyResponse.builder()
                .survey(survey)
                .userId(userId)
                .build());
 
        // Build a question map for O(1) lookup
        Map<Long, SurveyQuestion> questionMap = survey.getQuestions().stream()
            .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));
 
        // Validate required questions are answered
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
            throw new IllegalArgumentException(
                "Required questions not answered: " + missing);
        }
 
        // Map answers
        List<SurveyAnswer> answers = request.getAnswers().stream()
            .map(ar -> buildAnswer(ar, surveyResponse, questionMap))
            .toList();
 
        surveyResponse.setAnswers(answers);
        surveyResponse.setStatus(SurveyResponse.ResponseStatus.COMPLETED);
        surveyResponse.setCompletedAt(LocalDateTime.now());
 
        SurveyResponse savedResponse = responseRepository.save(surveyResponse);
 
        // Increment response counter on survey
        surveyRepository.incrementResponseCount(surveyId);
 
        // Grant reward
        SurveyReward reward = rewardService.grantReward(savedResponse);
 
        return SubmissionResult.builder()
            .responseId(savedResponse.getId())
            .status(savedResponse.getStatus())
            .reward(mapper.toRewardInfo(reward))
            .message("¡Gracias por completar la encuesta! Tu recompensa ha sido acreditada.")
            .build();
    }
 
    // ─── User: rewards summary ────────────────────────────────────────────────
 
    @Transactional(readOnly = true)
    public UserRewardsSummary getUserRewardsSummary(Long userId) {
        return rewardService.getUserRewardsSummary(userId);
    }
 
    // ─── Private helpers ──────────────────────────────────────────────────────
 
    private Survey findSurveyOrThrow(Long id) {
        return surveyRepository.findById(id)
            .orElseThrow(() -> new SurveyNotFoundException(id));
    }
 
    private void validateUserCanStartSurvey(Long surveyId, Long userId, Survey survey) {
        if (!survey.isActive()) {
            throw new SurveyNotActiveException(surveyId);
        }
        boolean alreadyDone = responseRepository.existsBySurveyIdAndUserIdAndStatusIn(
            surveyId, userId,
            List.of(SurveyResponse.ResponseStatus.COMPLETED, SurveyResponse.ResponseStatus.REWARDED));
        if (alreadyDone) {
            throw new SurveyAlreadyCompletedException();
        }
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
                    QuestionOption option = QuestionOption.builder()
                        .question(question)
                        .text(qr.getOptions().get(oi))
                        .orderIndex(oi)
                        .build();

                    options.add(option);
                }

                question.setOptions(options);
            }

            questions.add(question);
        }

        survey.setQuestions(questions);
    }
 
    private SurveyAnswer buildAnswer(AnswerRequest ar,
                                     SurveyResponse surveyResponse,
                                     Map<Long, SurveyQuestion> questionMap) {
        SurveyQuestion question = questionMap.get(ar.getQuestionId());
        if (question == null) {
            throw new IllegalArgumentException("Question not found: " + ar.getQuestionId());
        }
 
        SurveyAnswer.SurveyAnswerBuilder builder = SurveyAnswer.builder()
            .surveyResponse(surveyResponse)
            .question(question);
 
        switch (question.getType()) {
            // YES_NO is stored as a plain text value ("YES" / "NO") —
            // options are frontend constants, not persisted in the DB.
            case TEXT, RATING, YES_NO -> builder.textAnswer(ar.getTextAnswer());
            case SINGLE_CHOICE -> {
                if (ar.getSelectedOptionId() != null) {
                    QuestionOption opt = question.getOptions().stream()
                        .filter(o -> o.getId().equals(ar.getSelectedOptionId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Option not found: " + ar.getSelectedOptionId()));
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
                            if (o == null) throw new IllegalArgumentException("Option not found: " + id);
                            return o;
                        }).toList();
                    builder.selectedOptions(selected);
                }
            }
        }
        return builder.build();
    }
}