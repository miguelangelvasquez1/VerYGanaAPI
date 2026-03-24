package com.verygana2.repositories.surveys;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.surveys.Survey;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {
 
    /**
     * Fetches active surveys that the user hasn't answered yet,
     * scored by how many targeting criteria match the user's profile.
     *
     * Score breakdown (max = 4):
     *   +1  category match
     *   +1  municipality match
     *   +1  age within range
     *   +1  gender match
     *
     * Surveys with no criteria set for a dimension still score 0 for that
     * dimension (they are shown but ranked lower than targeted ones).
     */
    @Query(value = """
        SELECT s.*,
            (
                -- Category match
                CASE WHEN EXISTS (
                    SELECT 1 FROM survey_categories sc
                    JOIN consumer_preferences ucp
                        ON ucp.category_id = sc.category_id
                    WHERE sc.survey_id = s.id
                      AND ucp.user_id = ?1
                ) THEN 1 ELSE 0 END
                +
                -- Municipality match
                CASE WHEN NOT EXISTS (
                    SELECT 1 FROM survey_municipalities sm WHERE sm.survey_id = s.id
                ) OR EXISTS (
                    SELECT 1 FROM survey_municipalities sm
                    JOIN consumer_details u ON u.municipality_code = sm.municipality_code
                    WHERE sm.survey_id = s.id AND u.user_id = ?1
                ) THEN 1 ELSE 0 END
                +
                -- Age match
                CASE WHEN (?3 IS NULL OR s.min_age IS NULL OR ?2 >= s.min_age)
                      AND (?3 IS NULL OR s.max_age IS NULL OR ?2 <= s.max_age)
                THEN 1 ELSE 0 END
                +
                -- Gender match
                CASE WHEN ?3 IS NULL
                      OR s.target_gender = ?3
                THEN 1 ELSE 0 END
            ) AS match_score
            FROM surveys s
            WHERE s.status = 'ACTIVE'
            AND (s.starts_at IS NULL OR s.starts_at <= ?4)
            AND (s.ends_at   IS NULL OR s.ends_at   >= ?4)
            AND (s.max_responses IS NULL OR s.response_count < s.max_responses)
            AND NOT EXISTS (
                SELECT 1 FROM survey_responses sr
                WHERE sr.survey_id = s.id
                    AND sr.user_id = ?1
                    AND sr.status IN ('COMPLETED','REWARDED')
            )
            ORDER BY match_score DESC, s.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM surveys s
            WHERE s.status = 'ACTIVE'
            AND (s.starts_at IS NULL OR s.starts_at <= ?4)
            AND (s.ends_at   IS NULL OR s.ends_at   >= ?4)
            AND (s.max_responses IS NULL OR s.response_count < s.max_responses)
            AND NOT EXISTS (
                SELECT 1 FROM survey_responses sr
                WHERE sr.survey_id = s.id
                    AND sr.user_id = ?1
                    AND sr.status IN ('COMPLETED','REWARDED')
            )
            """,
            nativeQuery = true)
    Page<Survey> findActiveSurveysRankedForUser(
        @Param("userId")    Long userId,       // ?1
        @Param("userAge")    Integer userAge,   // ?2
        @Param("userGender") String userGender, // ?3
        @Param("now")        LocalDateTime now, // ?4
        Pageable pageable
    );
 
    @Modifying
    @Query("UPDATE Survey s SET s.responseCount = s.responseCount + 1 WHERE s.id = :surveyId")
    void incrementResponseCount(@Param("surveyId") Long surveyId);
 
    @Query("SELECT s FROM Survey s WHERE s.status = :status")
    List<Survey> findByStatus(@Param("status") Survey.SurveyStatus status);
}