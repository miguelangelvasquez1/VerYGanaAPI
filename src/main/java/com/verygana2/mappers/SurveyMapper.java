package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.verygana2.dtos.survey.AvailableSurveyDTO;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.SurveyDetailDTO;
import com.verygana2.dtos.survey.SurveySessionDTO;
import com.verygana2.dtos.survey.OptionResponse;
import com.verygana2.dtos.survey.QuestionResponse;
import com.verygana2.dtos.survey.SurveyAdminDetailDTO;
import com.verygana2.dtos.survey.SurveyCommercialDetailDTO;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
import com.verygana2.dtos.survey.submission.RewardInfo;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.surveys.QuestionOption;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.surveys.SurveyReward;

@Mapper(
    componentModel       = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = LocationMapper.class
)
public interface SurveyMapper {

    // ─── Survey → SurveyResponseDTO ──────────────────────────────────────────

    @Mapping(target = "categoryNames",    expression = "java(categoriesToNames(survey.getTargetAudience() != null ? survey.getTargetAudience().getCategories() : java.util.List.of()))")
    @Mapping(target = "municipalityNames",expression = "java(municipalitiesToNames(survey.getTargetAudience() != null ? survey.getTargetAudience().getTargetMunicipalities() : java.util.List.of()))")
    @Mapping(target = "minAge",        source = "targetAudience.minAge")
    @Mapping(target = "maxAge",        source = "targetAudience.maxAge")
    @Mapping(target = "targetGender",  source = "targetAudience.targetGender")
    @Mapping(target = "questions",     source = "survey.questions")
    SurveyResponseDTO toResponse(Survey survey);

    // ─── Survey → AvailableSurveyDTO ─────────────────────────────────────────

    @Mapping(target = "totalQuestions", expression = "java(survey.getQuestions().size())")
    AvailableSurveyDTO toAvailableSurvey(Survey survey);

    // ─── Survey → SurveyDetailDTO ────────────────────────────────────────────

    @Mapping(target = "categoryNames",  expression = "java(categoriesToNames(survey.getTargetAudience() != null ? survey.getTargetAudience().getCategories() : java.util.List.of()))")
    @Mapping(target = "totalQuestions", expression = "java(survey.getQuestions().size())")
    SurveyDetailDTO toSurveyDetail(Survey survey);

    // ─── Survey → SurveySessionDTO ───────────────────────────────────────────

    @Mapping(target = "questions", source = "survey.questions")
    SurveySessionDTO toSurveySessionDTO(Survey survey);

    // ─── Survey → SurveySummaryResponse ──────────────────────────────────────

    @Mapping(target = "alreadyCompleted", ignore = true)
    @Mapping(target = "totalResponses",   source = "survey.responseCount")
    @Mapping(target = "totalQuestions",   expression = "java(survey.getQuestions().size())")
    SurveySummaryResponse toSummaryResponse(Survey survey);

    // ─── Survey → SurveyAdminDetailDTO ───────────────────────────────────────

    @Mapping(target = "categoryNames",    expression = "java(categoriesToNames(survey.getTargetAudience() != null ? survey.getTargetAudience().getCategories() : java.util.List.of()))")
    @Mapping(target = "municipalityNames",expression = "java(municipalitiesToNames(survey.getTargetAudience() != null ? survey.getTargetAudience().getTargetMunicipalities() : java.util.List.of()))")
    @Mapping(target = "minAge",           source = "targetAudience.minAge")
    @Mapping(target = "maxAge",           source = "targetAudience.maxAge")
    @Mapping(target = "targetGender",     source = "targetAudience.targetGender")
    @Mapping(target = "questions",        source = "survey.questions")
    @Mapping(target = "creatorId",        source = "survey.creator.id")
    @Mapping(target = "companyName",      source = "survey.creator.companyName")
    @Mapping(target = "creatorEmail",     source = "survey.creator.user.email")
    @Mapping(target = "totalSessions",    ignore = true)
    @Mapping(target = "activeSessions",   ignore = true)
    @Mapping(target = "completedSessions",ignore = true)
    @Mapping(target = "expiredSessions",  ignore = true)
    @Mapping(target = "abandonedSessions",ignore = true)
    @Mapping(target = "totalBudgetCents", ignore = true)
    @Mapping(target = "spentCents",       ignore = true)
    @Mapping(target = "completionRate",   ignore = true)
    SurveyAdminDetailDTO toAdminDetail(Survey survey);

    // ─── Survey → SurveyCommercialDetailDTO ──────────────────────────────────

    @Mapping(target = "categories",        source = "targetAudience.categories")
    @Mapping(target = "targetMunicipalities", source = "targetAudience.targetMunicipalities")
    @Mapping(target = "minAge",            source = "targetAudience.minAge")
    @Mapping(target = "maxAge",            source = "targetAudience.maxAge")
    @Mapping(target = "targetGender",      source = "targetAudience.targetGender")
    @Mapping(target = "questions",         source = "survey.questions")
    @Mapping(target = "completedSessions", ignore = true)
    @Mapping(target = "totalBudgetCents",  ignore = true)
    SurveyCommercialDetailDTO toCommercialDetail(Survey survey);

    // ─── SurveyQuestion → QuestionResponse ───────────────────────────────────

    @Mapping(target = "options", source = "question.options")
    QuestionResponse toQuestionResponse(SurveyQuestion question);

    List<QuestionResponse> toQuestionResponseList(List<SurveyQuestion> questions);

    // ─── QuestionOption → OptionResponse ─────────────────────────────────────

    OptionResponse toOptionResponse(QuestionOption option);

    List<OptionResponse> toOptionResponseList(List<QuestionOption> options);

    // ─── SurveyReward → RewardInfo ────────────────────────────────────────────

    @Mapping(target = "rewardId", source = "reward.id")
    RewardInfo toRewardInfo(SurveyReward reward);

    // ─── CreateSurveyRequest → Survey ────────────────────────────────────────

    @Mapping(target = "id",                          ignore = true)
    @Mapping(target = "status",                      ignore = true)
    @Mapping(target = "responseCount",               ignore = true)
    @Mapping(target = "targetAudience",              ignore = true)
    @Mapping(target = "questions",                   ignore = true)
    @Mapping(target = "sessions",                    ignore = true)
    @Mapping(target = "createdAt",                   ignore = true)
    @Mapping(target = "updatedAt",                   ignore = true)
    @Mapping(target = "rewardAmountPerQuestionCents",ignore = true)
    @Mapping(target = "creator",                     ignore = true)
    @Mapping(target = "endsAt",                      ignore = true)
    Survey fromCreateRequest(CreateSurveyRequest request);

    // ─── Helpers ──────────────────────────────────────────────────────────────

    default List<String> categoriesToNames(List<Category> categories) {
        if (categories == null) return List.of();
        return categories.stream().map(Category::getName).toList();
    }

    default List<String> municipalitiesToNames(List<Municipality> municipalities) {
        if (municipalities == null) return List.of();
        return municipalities.stream().map(Municipality::getName).toList();
    }
}
