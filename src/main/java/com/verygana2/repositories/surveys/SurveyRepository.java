package com.verygana2.repositories.surveys;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.surveys.Survey;

import jakarta.persistence.LockModeType;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Page<Survey> findAllByCreatorIdOrderByCreatedAtDesc(Pageable pageable, Long creatorId);

    Page<Survey> findAllByCreatorIdAndStatusOrderByCreatedAtDesc(Pageable pageable, Long creatorId, Survey.SurveyStatus status);

    Page<Survey> findAllByStatusOrderByCreatedAtDesc(Pageable pageable, Survey.SurveyStatus status);

    long countByCreatorIdAndStatus(Long creatorId, Survey.SurveyStatus status);

    /** Counts surveys still consuming a plan slot (everything except final states: CLOSED/"cancelled" and COMPLETED). */
    long countByCreatorIdAndStatusNotIn(Long creatorId, List<Survey.SurveyStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Survey s WHERE s.id = :id")
    Optional<Survey> findByIdForUpdate(@Param("id") Long id);

    /**
     * Fetches active surveys eligible for the user, ranked by category affinity.
     *
     * Hard filters (mandatory — survey excluded if it fails):
     *   - Status ACTIVE, within date window
     *   - Quota: responseCount + active non-expired sessions < maxResponses
     *   - Age range, gender, municipality
     *   - Not already completed by this user
     *
     * Score (ranking only):
     *   - Number of survey categories that match the user's preferences
     */
    @Query(value = """
        SELECT s.*,
            (
                SELECT COUNT(*)
                FROM target_audience_categories tac
                JOIN consumer_preferences ucp ON ucp.category_id = tac.category_id
                WHERE tac.target_audience_id = s.target_audience_id AND ucp.user_id = ?1
            ) AS match_score
        FROM surveys s
        LEFT JOIN target_audiences ta ON ta.id = s.target_audience_id
        WHERE s.status = 'ACTIVE'
        AND (s.starts_at IS NULL OR s.starts_at <= ?4)
        AND (s.ends_at   IS NULL OR s.ends_at   >= ?4)
        AND (s.max_responses IS NULL OR (
            s.response_count + (
                SELECT COUNT(*) FROM survey_sessions ss
                WHERE ss.survey_id = s.id
                  AND ss.status = 'ACTIVE'
                  AND ss.expires_at > CURRENT_TIMESTAMP
            )
        ) < s.max_responses)
        AND (?2 IS NULL OR ta.min_age IS NULL OR ?2 >= ta.min_age)
        AND (?2 IS NULL OR ta.max_age IS NULL OR ?2 <= ta.max_age)
        AND (ta.target_gender IS NULL OR ?3 IS NULL OR ta.target_gender = ?3)
        AND (
            NOT EXISTS (SELECT 1 FROM target_audience_municipalities tam WHERE tam.target_audience_id = s.target_audience_id)
            OR EXISTS (
                SELECT 1 FROM target_audience_municipalities tam
                JOIN consumer_details cd ON cd.municipality_code = tam.municipality_code
                WHERE tam.target_audience_id = s.target_audience_id AND cd.user_id = ?1
            )
        )
        AND NOT EXISTS (
            SELECT 1 FROM survey_sessions ss
            WHERE ss.survey_id = s.id
              AND ss.consumer_id = ?1
              AND ss.status = 'COMPLETED'
        )
        ORDER BY match_score DESC, s.created_at DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM surveys s
        LEFT JOIN target_audiences ta ON ta.id = s.target_audience_id
        WHERE s.status = 'ACTIVE'
        AND (s.starts_at IS NULL OR s.starts_at <= ?4)
        AND (s.ends_at   IS NULL OR s.ends_at   >= ?4)
        AND (s.max_responses IS NULL OR (
            s.response_count + (
                SELECT COUNT(*) FROM survey_sessions ss
                WHERE ss.survey_id = s.id
                  AND ss.status = 'ACTIVE'
                  AND ss.expires_at > CURRENT_TIMESTAMP
            )
        ) < s.max_responses)
        AND (?2 IS NULL OR ta.min_age IS NULL OR ?2 >= ta.min_age)
        AND (?2 IS NULL OR ta.max_age IS NULL OR ?2 <= ta.max_age)
        AND (ta.target_gender IS NULL OR ?3 IS NULL OR ta.target_gender = ?3)
        AND (
            NOT EXISTS (SELECT 1 FROM target_audience_municipalities tam WHERE tam.target_audience_id = s.target_audience_id)
            OR EXISTS (
                SELECT 1 FROM target_audience_municipalities tam
                JOIN consumer_details cd ON cd.municipality_code = tam.municipality_code
                WHERE tam.target_audience_id = s.target_audience_id AND cd.user_id = ?1
            )
        )
        AND NOT EXISTS (
            SELECT 1 FROM survey_sessions ss
            WHERE ss.survey_id = s.id
              AND ss.consumer_id = ?1
              AND ss.status = 'COMPLETED'
        )
        """,
            nativeQuery = true)
    Page<Survey> findActiveSurveysRankedForUser(
        @Param("userId")     Long userId,
        @Param("userAge")    Integer userAge,
        @Param("userGender") String userGender,
        @Param("now")        LocalDateTime now,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE Survey s SET s.responseCount = s.responseCount + 1 WHERE s.id = :surveyId")
    void incrementResponseCount(@Param("surveyId") Long surveyId);

    /**
     * Atomically marks a survey COMPLETED once its response quota is reached.
     * Operates directly in SQL (not via the loaded entity) so it can't race with
     * {@link #incrementResponseCount} or overwrite it with a stale in-memory value.
     */
    @Modifying
    @Query("""
        UPDATE Survey s SET s.status = :completed
        WHERE s.id = :surveyId
          AND s.status IN :fromStatuses
          AND s.maxResponses IS NOT NULL
          AND s.responseCount >= s.maxResponses
        """)
    void completeIfQuotaReached(
            @Param("surveyId")      Long surveyId,
            @Param("completed")     Survey.SurveyStatus completed,
            @Param("fromStatuses")  List<Survey.SurveyStatus> fromStatuses);

    @Query("SELECT s FROM Survey s WHERE s.status = :status")
    List<Survey> findByStatus(@Param("status") Survey.SurveyStatus status);
}
