package com.verygana2.dtos.survey;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

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
public class SurveyAdminDetailDTO {

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
    private ZonedDateTime updatedAt;

    // ─── Targeting ────────────────────────────────────────────────────────────
    private List<String> categoryNames;
    private List<String> municipalityNames;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;

    // ─── Questions ────────────────────────────────────────────────────────────
    private int totalQuestions;
    private List<QuestionResponse> questions;

    // ─── Creator ──────────────────────────────────────────────────────────────
    private Long creatorId;
    private String companyName;
    private String creatorEmail;

    // ─── Session stats ────────────────────────────────────────────────────────
    private long totalSessions;
    private long activeSessions;
    private long completedSessions;
    private long expiredSessions;
    private long abandonedSessions;

    // ─── Financial ────────────────────────────────────────────────────────────
    /** rewardPerQuestion × questionCount × maxResponses; null when maxResponses is open-ended */
    private Long totalBudgetCents;
    /** rewardPerQuestion × questionCount × completedSessions */
    private long spentCents;
    /** completedSessions / maxResponses × 100 (or over totalSessions when open-ended) */
    private double completionRate;
}
