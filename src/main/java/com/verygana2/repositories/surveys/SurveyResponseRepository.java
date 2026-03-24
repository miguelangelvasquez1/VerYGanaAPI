package com.verygana2.repositories.surveys;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.surveys.SurveyResponse;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
 
    Optional<SurveyResponse> findBySurveyIdAndUserId(Long surveyId, Long userId);
 
    boolean existsBySurveyIdAndUserIdAndStatusIn(Long surveyId, Long userId,
        java.util.List<SurveyResponse.ResponseStatus> statuses);
 
    Page<SurveyResponse> findByUserId(Long userId, Pageable pageable);
 
    @Query("SELECT COUNT(r) FROM SurveyResponse r WHERE r.userId = :userId AND r.status = 'COMPLETED'")
    long countCompletedByUser(@Param("userId") Long userId);

    /** All responses for a survey (with answers eagerly fetched) — analytics & export. */
    @Query("""
        SELECT r FROM SurveyResponse r
        LEFT JOIN FETCH r.answers a
        LEFT JOIN FETCH a.question
        WHERE r.survey.id = :surveyId
        """)
    List<SurveyResponse> findBySurveyId(@Param("surveyId") Long surveyId);
 
    /** Paginated responses — admin table. */
    @Query(
        value      = "SELECT r FROM SurveyResponse r WHERE r.survey.id = :surveyId",
        countQuery = "SELECT COUNT(r) FROM SurveyResponse r WHERE r.survey.id = :surveyId"
    )
    Page<SurveyResponse> findBySurveyId(@Param("surveyId") Long surveyId, Pageable pageable);
}