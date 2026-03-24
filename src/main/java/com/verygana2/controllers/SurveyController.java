package com.verygana2.controllers;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.survey.SurveyAnalyticsDTO;
import com.verygana2.dtos.survey.SurveyResponseDTO;
import com.verygana2.dtos.survey.SurveyResponseDetailDTO;
import com.verygana2.dtos.survey.SurveySummaryResponse;
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
 
    /**
     * Returns surveys ranked by how many targeting criteria match the
     * authenticated user (categories, municipality, age, gender).
     */
    @GetMapping
    public ResponseEntity<Page<SurveySummaryResponse>> getAvailableSurveys(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 10) Pageable pageable) {
 
        return ResponseEntity.ok(surveyService.getSurveysForUser(jwt.getClaim("userId"), pageable));
    }

    /**
     * Returns the full survey with all questions (user must not have completed it).
     */
    @GetMapping("/{surveyId}")
    public ResponseEntity<SurveyResponseDTO> getSurveyDetail(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal Jwt jwt) {
 
        return ResponseEntity.ok(surveyService.getSurveyDetail(surveyId, jwt.getClaim("userId")));
    }
 
    /**
     * Submits answers and triggers reward processing.
     */
    @PostMapping("/submit")
    public ResponseEntity<SubmissionResult> submitSurvey(
            @Valid @RequestBody SubmitSurveyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(surveyService.submitSurvey(request, jwt.getClaim("userId")));
    }
 
    /**
     * Returns the authenticated user's reward history and totals.
     */
    @GetMapping("/rewards/summary")
    public ResponseEntity<UserRewardsSummary> getRewardsSummary(
            @AuthenticationPrincipal Jwt jwt) {
 
        return ResponseEntity.ok(surveyService.getUserRewardsSummary(jwt.getClaim("userId")));
    }

    // FOR ADMIN:

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/cost-per-response")
    public ResponseEntity<Map<String, BigDecimal>> getCostPerResponse() {

        return ResponseEntity.ok(Map.of("costPerResponse", pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<SurveyResponseDTO> createSurvey(
            @Valid @RequestBody CreateSurveyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(surveyService.createSurvey(request, jwt.getClaim("userId")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<PagedResponse<SurveySummaryResponse>> getAllSurveysForAdmin(
            @PageableDefault(size = 10) Pageable pageable) {
 
        return ResponseEntity.ok(surveyService.getAllSurveys(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{surveyId}")
    public ResponseEntity<SurveyResponseDTO> getSurveyDetailForAdmin(
            @PathVariable Long surveyId) {
 
        return ResponseEntity.ok(surveyService.getSurveyDetailForAdmin(surveyId));
    }
 
    /**
     * PATCH /api/v1/admin/surveys/{surveyId}/publish
     * Moves a DRAFT survey to ACTIVE.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{surveyId}/publish")
    public ResponseEntity<SurveyResponseDTO> publishSurvey(
            @PathVariable Long surveyId) {
 
        return ResponseEntity.ok(surveyService.publishSurvey(surveyId));
    }
 
    /**
     * PATCH /api/v1/admin/surveys/{surveyId}/status
     * Updates the survey status (PAUSED, CLOSED, ACTIVE, DRAFT).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{surveyId}/status")
    public ResponseEntity<SurveyResponseDTO> updateStatus(
            @PathVariable Long surveyId,
            @RequestParam Survey.SurveyStatus status) {
 
        return ResponseEntity.ok(surveyService.updateSurveyStatus(surveyId, status));
    }

    /**
     * Responses for a survey, paginated. Admin-only endpoint.
     */
    @GetMapping("/admin/{surveyId}/responses")
    public ResponseEntity<PagedResponse<SurveyResponseDetailDTO>> getResponses(
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
 
        size = Math.min(size, 100);  // safety cap
        PageRequest pageable = PageRequest.of(page, size, Sort.by("completedAt").descending());
 
        return ResponseEntity.ok(responseService.getPagedResponses(surveyId, pageable));
    }

    @GetMapping("/admin/{surveyId}/analytics")
    public ResponseEntity<SurveyAnalyticsDTO> getAnalytics(
            @PathVariable Long surveyId) {
 
        return ResponseEntity.ok(responseService.getAnalytics(surveyId));
    }

     @GetMapping("/admin/{surveyId}/responses/export")
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
}