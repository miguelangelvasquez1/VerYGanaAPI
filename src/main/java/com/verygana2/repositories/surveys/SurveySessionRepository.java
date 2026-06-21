package com.verygana2.repositories.surveys;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.surveys.SurveySession;

@Repository
public interface SurveySessionRepository extends JpaRepository<SurveySession, Long> {

    @Query("SELECT s FROM SurveySession s WHERE s.survey.id = :surveyId AND s.consumer.id = :consumerId AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    Optional<SurveySession> findActiveNonExpired(
            @Param("surveyId") Long surveyId,
            @Param("consumerId") Long consumerId,
            @Param("now") ZonedDateTime now);

    @Query("SELECT s FROM SurveySession s WHERE s.survey.id = :surveyId AND s.consumer.id = :consumerId AND s.status = 'ACTIVE'")
    Optional<SurveySession> findActiveBySurveyAndConsumer(
            @Param("surveyId") Long surveyId,
            @Param("consumerId") Long consumerId);

    boolean existsBySurveyIdAndConsumerIdAndStatus(Long surveyId, Long consumerId, SurveySession.SessionStatus status);

    @Query("SELECT COUNT(s) FROM SurveySession s WHERE s.survey.id = :surveyId AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    long countActiveNonExpiredBySurveyId(@Param("surveyId") Long surveyId, @Param("now") ZonedDateTime now);

    long countBySurveyIdAndStatus(Long surveyId, SurveySession.SessionStatus status);

    @Query("SELECT COUNT(s) FROM SurveySession s WHERE s.consumer.id = :consumerId AND s.status = 'COMPLETED'")
    long countCompletedByConsumer(@Param("consumerId") Long consumerId);

    Page<SurveySession> findByConsumerId(Long consumerId, Pageable pageable);

    @Query("""
        SELECT s FROM SurveySession s
        LEFT JOIN FETCH s.answers a
        LEFT JOIN FETCH a.question
        WHERE s.survey.id = :surveyId
        """)
    List<SurveySession> findBySurveyIdWithAnswers(@Param("surveyId") Long surveyId);

    @Query(
        value      = "SELECT s FROM SurveySession s WHERE s.survey.id = :surveyId",
        countQuery = "SELECT COUNT(s) FROM SurveySession s WHERE s.survey.id = :surveyId"
    )
    Page<SurveySession> findBySurveyId(@Param("surveyId") Long surveyId, Pageable pageable);
}
