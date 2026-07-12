package com.verygana2.dtos.survey;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Category;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.surveys.Survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SurveyCommercialDetailDTO {

    // ─── Survey info ──────────────────────────────────────────────────────────
    private Long id;
    private String title;
    private String description;
    private Survey.SurveyStatus status;
    private Long rewardAmountPerQuestionCents;
    private Integer maxResponses;
    private Integer responseCount;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private ZonedDateTime createdAt;

    // ─── Targeting ────────────────────────────────────────────────────────────
    private List<Category> categories;
    private List<MunicipalityResponseDTO> targetMunicipalities;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;

    // ─── Questions ────────────────────────────────────────────────────────────
    private List<QuestionResponse> questions;

    // ─── Budget ───────────────────────────────────────────────────────────────
    /** null when maxResponses is open-ended */
    private Long totalBudgetCents;
}
