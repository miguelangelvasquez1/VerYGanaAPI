package com.verygana2.controllers;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.survey.AvailableSurveyDTO;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.SurveyDetailDTO;
import com.verygana2.dtos.survey.StartSurveyResponse;
import com.verygana2.dtos.survey.SurveyAdminDetailDTO;
import com.verygana2.dtos.survey.SurveyAnalyticsDTO;
import com.verygana2.dtos.survey.SurveyCommercialDetailDTO;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveyResponseDetailDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
import com.verygana2.dtos.survey.UpdateSurveyRequest;
import com.verygana2.dtos.survey.submission.SubmissionResult;
import com.verygana2.dtos.survey.submission.SubmitSurveyRequest;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.surveys.Survey;
import com.verygana2.services.PricingConfigService;
import com.verygana2.services.surveys.SurveyResponseService;
import com.verygana2.services.surveys.SurveyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final PricingConfigService pricingConfigService;
    private final SurveyResponseService responseService;

    @PreAuthorize("hasRole('COMMERCIAL')")
    @PostMapping
    public ResponseEntity<SurveyResponseDTO> createSurvey(
            @Valid @RequestBody CreateSurveyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(surveyService.createSurvey(request, jwt.getClaim("userId")));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @PutMapping("/{surveyId}")
    public ResponseEntity<SurveyCommercialDetailDTO> updateSurvey(
            @PathVariable Long surveyId,
            @Valid @RequestBody UpdateSurveyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.updateSurvey(surveyId, request, jwt.getClaim("userId")));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @PatchMapping("/{surveyId}/publish")
    public ResponseEntity<SurveyResponseDTO> publishSurvey(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.publishSurvey(surveyId, jwt.getClaim("userId")));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/commercial")
    public ResponseEntity<PagedResponse<SurveySummaryResponse>> getAllSurveysForCommercial(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) Survey.SurveyStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.getAllSurveysForCommercial(pageable, jwt.getClaim("userId"), status));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/commercial/{surveyId}")
    public ResponseEntity<SurveyCommercialDetailDTO> getSurveyCommercialDetail(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.getSurveyCommercialDetail(surveyId, jwt.getClaim("userId")));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/cost-per-question")
    public ResponseEntity<Map<String, Long>> getCostPerQuestion() {
        return ResponseEntity.ok(Map.of("costPerQuestion",
                pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY_REWARD_PER_QUESTION_CENTS) / 100L));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/{surveyId}/responses")
    public ResponseEntity<PagedResponse<SurveyResponseDetailDTO>> getResponses(
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("completedAt").descending());
        return ResponseEntity.ok(responseService.getPagedResponses(surveyId, pageable));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/{surveyId}/analytics")
    public ResponseEntity<SurveyAnalyticsDTO> getAnalytics(
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(responseService.getAnalytics(surveyId));
    }

    @PreAuthorize("hasRole('COMMERCIAL')")
    @GetMapping("/{surveyId}/responses/export")
    public ResponseEntity<byte[]> exportResponses(
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = "csv") String format) {

        byte[] fileBytes;
        String filename;
        MediaType mediaType;

        switch (format.toLowerCase()) {
            case "xlsx" -> {
                fileBytes = responseService.exportAsXlsx(surveyId);
                filename  = "encuesta-" + surveyId + "-respuestas.xlsx";
                mediaType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }
            default -> {
                fileBytes = responseService.exportAsCsv(surveyId);
                filename  = "encuesta-" + surveyId + "-respuestas.csv";
                mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(fileBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    // CONSUMER

    @GetMapping
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<PagedResponse<AvailableSurveyDTO>> getAvailableSurveys(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(surveyService.getSurveysForUser(jwt.getClaim("userId"), pageable));
    }

    @GetMapping("/{surveyId}")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<SurveyDetailDTO> getSurveyDetail(
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(surveyService.getSurveyDetail(surveyId));
    }

    /**
     * Creates a new session (or resumes an active one) for the authenticated consumer.
     * Returns the session ID, expiry time, and full survey with questions.
     */
    @PostMapping("/{surveyId}/start")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<StartSurveyResponse> startSurvey(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.startSurvey(surveyId, jwt.getClaim("userId")));
    }

    /**
     * Submits answers for an existing active session and triggers reward processing.
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<SubmissionResult> submitSurvey(
            @Valid @RequestBody SubmitSurveyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(surveyService.submitSurvey(request, jwt.getClaim("userId")));
    }

    @GetMapping("/rewards/summary")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<UserRewardsSummary> getRewardsSummary(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.getUserRewardsSummary(jwt.getClaim("userId")));
    }

    // ADMIN

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<PagedResponse<SurveySummaryResponse>> getAllSurveysForAdmin(
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(surveyService.getAllSurveys(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{surveyId}")
    public ResponseEntity<SurveyAdminDetailDTO> getSurveyAdminDetail(
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(surveyService.getSurveyAdminDetail(surveyId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{surveyId}/status")
    public ResponseEntity<SurveyResponseDTO> updateStatus(
            @PathVariable Long surveyId,
            @RequestParam Survey.SurveyStatus status) {

        return ResponseEntity.ok(surveyService.updateSurveyStatus(surveyId, status));
    }

    /** Commercial-only status transition, restricted to ACTIVE / PAUSED / CLOSED ("cancelled"). */
    @PreAuthorize("hasRole('COMMERCIAL')")
    @PatchMapping("/{surveyId}/commercial-status")
    public ResponseEntity<SurveyResponseDTO> updateStatusAsCommercial(
            @PathVariable Long surveyId,
            @RequestParam Survey.SurveyStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(surveyService.updateSurveyStatusAsCommercial(surveyId, status, jwt.getClaim("userId")));
    }
}
