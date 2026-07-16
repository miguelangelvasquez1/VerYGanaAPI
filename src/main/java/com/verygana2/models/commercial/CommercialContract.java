package com.verygana2.models.commercial;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.ContractStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Pasos 9-11: Contrato Marco generado (PDF) para un onboarding comercial, y su
 * ciclo de revisión (empresario -> VERYGANA).
 */
@Entity
@Table(name = "commercial_contracts")
@Data
public class CommercialContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_onboarding_id", nullable = false, unique = true)
    private CommercialOnboarding onboarding;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "generated_at", nullable = false)
    private ZonedDateTime generatedAt;

    @Column(name = "business_approved_at")
    private ZonedDateTime businessApprovedAt;

    @Column(name = "admin_reviewer_user_id")
    private Long adminReviewerUserId;

    @Column(name = "admin_reviewed_at")
    private ZonedDateTime adminReviewedAt;

    @Column(name = "admin_decision_notes", length = 1000)
    private String adminDecisionNotes;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = ZonedDateTime.now();
        }
        if (version == 0) {
            version = 1;
        }
    }
}
