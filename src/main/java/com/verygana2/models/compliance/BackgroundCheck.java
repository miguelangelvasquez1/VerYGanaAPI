package com.verygana2.models.compliance;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.BackgroundCheckStatus;
import com.verygana2.models.enums.BackgroundCheckType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Consulta de antecedentes (ZapSign checks) solicitada por compliance sobre un Contrato Marco. */
@Entity
@Table(name = "background_checks", indexes = {
        @Index(name = "idx_background_check_contract_id", columnList = "contract_id"),
        @Index(name = "idx_background_check_check_id", columnList = "check_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackgroundCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Intentional non-FK, igual que ScreeningResult.userId — evita problemas de
    // serialización/lazy-loading al exponer esta entidad directamente en el panel.
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "check_id", unique = true, length = 60)
    private String checkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 20)
    private BackgroundCheckType checkType;

    @Column(name = "country", length = 5)
    private String country;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "subject_document", length = 30)
    private String subjectDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackgroundCheckStatus status;

    @Column(name = "score")
    private Double score;

    @Column(name = "pdf_report_url", length = 500)
    private String pdfReportUrl;

    @Column(name = "requested_by_officer_id")
    private Long requestedByOfficerId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = ZonedDateTime.now();
        }
    }
}
