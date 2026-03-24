package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.OptionResponse;
import com.verygana2.dtos.survey.QuestionResponse;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
import com.verygana2.dtos.survey.submission.RewardInfo;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.surveys.QuestionOption;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.surveys.SurveyReward;

/**
 * MapStruct mapper for the Survey module.
 *
 * Conventions:
 *  - componentModel = "spring"    → generates @Component, injected via @Autowired / constructor
 *  - unmappedTargetPolicy = ERROR → compilation fails if a target field is forgotten
 *  - nullValuePropertyMappingStrategy = IGNORE → safe for partial updates
 */
@Mapper(
    componentModel       = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SurveyMapper {
 
    // ─── Survey → SurveyResponse (full detail) ───────────────────────────────
 
    @Mapping(target = "categoryNames", expression = "java(categoriesToNames(survey.getCategories()))")
    @Mapping(target = "municipalityNames", expression = "java(municipalitiesToNames(survey.getTargetMunicipalities()))")
    @Mapping(target = "questions", source = "survey.questions")
    SurveyResponseDTO toResponse(Survey survey);
 
    // ─── Survey → SurveySummaryResponse (list item) ──────────────────────────
 
    // alreadyCompleted is not in the entity; service sets it manually when needed
    @Mapping(target = "alreadyCompleted", ignore = true)
    @Mapping(target = "totalResponses", source = "survey.responseCount")
    SurveySummaryResponse toSummaryResponse(Survey survey);
 
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
    //
    //  Ignored fields and why:
    //   id, createdAt, updatedAt  → JPA-managed
    //   status                    → forced to DRAFT by the service after mapping
    //   responseCount             → entity default (0)
    //   categories                → resolved in service via DB lookup (categoryIds)
    //   targetMunicipalities      → resolved in service via DB lookup (municipalityCodes)
    //   questions                 → built in @AfterMapping (need orderIndex auto-assign)
    //   responses                 → runtime collection, not set on creation
 
    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "status",               ignore = true)
    @Mapping(target = "responseCount",        ignore = true)
    @Mapping(target = "categories",           ignore = true)
    @Mapping(target = "targetMunicipalities", ignore = true)
    @Mapping(target = "questions",            ignore = true)
    @Mapping(target = "responses",            ignore = true)
    @Mapping(target = "createdAt",            ignore = true)
    @Mapping(target = "updatedAt",            ignore = true)
    @Mapping(target = "rewardAmount",         ignore = true)
    @Mapping(target = "creator",              ignore = true)
    Survey fromCreateRequest(CreateSurveyRequest request);
 
    // ─── Default helpers (used in @Mapping expressions) ──────────────────────
 
    default List<String> categoriesToNames(List<Category> categories) {
        if (categories == null) return List.of();
        return categories.stream().map(Category::getName).toList();
    }
 
    default List<String> municipalitiesToNames(List<Municipality> municipalities) {
        if (municipalities == null) return List.of();
        return municipalities.stream().map(Municipality::getName).toList();
    }
}