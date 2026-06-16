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

    long countByCreatorIdAndStatus(Long creatorId, Survey.SurveyStatus status);

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
                FROM survey_categories sc
                JOIN consumer_preferences ucp ON ucp.category_id = sc.category_id
                WHERE sc.survey_id = s.id AND ucp.user_id = ?1
            ) AS match_score
        FROM surveys s
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
        AND (?2 IS NULL OR s.min_age IS NULL OR ?2 >= s.min_age)
        AND (?2 IS NULL OR s.max_age IS NULL OR ?2 <= s.max_age)
        AND (s.target_gender IS NULL OR ?3 IS NULL OR s.target_gender = ?3)
        AND (
            NOT EXISTS (SELECT 1 FROM survey_municipalities sm WHERE sm.survey_id = s.id)
            OR EXISTS (
                SELECT 1 FROM survey_municipalities sm
                JOIN consumer_details cd ON cd.municipality_code = sm.municipality_code
                WHERE sm.survey_id = s.id AND cd.user_id = ?1
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
        AND (?2 IS NULL OR s.min_age IS NULL OR ?2 >= s.min_age)
        AND (?2 IS NULL OR s.max_age IS NULL OR ?2 <= s.max_age)
        AND (s.target_gender IS NULL OR ?3 IS NULL OR s.target_gender = ?3)
        AND (
            NOT EXISTS (SELECT 1 FROM survey_municipalities sm WHERE sm.survey_id = s.id)
            OR EXISTS (
                SELECT 1 FROM survey_municipalities sm
                JOIN consumer_details cd ON cd.municipality_code = sm.municipality_code
                WHERE sm.survey_id = s.id AND cd.user_id = ?1
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

    @Query("SELECT s FROM Survey s WHERE s.status = :status")
    List<Survey> findByStatus(@Param("status") Survey.SurveyStatus status);
}
