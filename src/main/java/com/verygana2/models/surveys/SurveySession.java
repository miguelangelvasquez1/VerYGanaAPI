package com.verygana2.models.surveys;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_sessions",
    indexes = {
        @Index(name = "idx_session_survey_consumer", columnList = "survey_id, consumer_id"),
        @Index(name = "idx_session_status_expires",  columnList = "status, expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SurveyAnswer> answers = new ArrayList<>();

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL)
    private SurveyReward reward;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    public boolean isExpiredByTime() {
        return status == SessionStatus.ACTIVE && ZonedDateTime.now().isAfter(expiresAt);
    }

    public enum SessionStatus {
        ACTIVE, COMPLETED, EXPIRED, ABANDONED
    }
}
