package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.userDetails.CommercialDetails;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Situación detectada que compliance debe revisar (4.8): por una regla versionada o por un
 * evento del expediente (cambio de actividades o de establecimientos). Informativa: no cambia
 * responsabilidades, no acredita incumplimiento y no aplica medidas. La revisión decide si
 * se pide información al empresario.
 */
@Entity
@Table(name = "compliance_alerts", indexes = {
        @Index(name = "idx_compliance_alert_open", columnList = "reviewed_at, detected_at")
})
@Getter
@Setter
public class ComplianceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Regla que la detectó, con su versión. Null si la originó un evento del expediente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", updatable = false)
    private ComplianceAlertRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 40, updatable = false)
    private InformationRequestTrigger trigger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false, updatable = false)
    private CommercialDetails commercial;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private ZonedDateTime detectedAt;

    /** Datos usados para detectarla, para poder reproducir la detección. */
    @Column(name = "data_used_json", nullable = false, length = 2000, updatable = false)
    private String dataUsedJson;

    /** Describe el hecho observado y su alcance; no afirma consecuencias jurídicas. */
    @Column(nullable = false, length = 1000, updatable = false)
    private String message;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    /** Requerimiento que compliance abrió a partir de la alerta, si lo hubo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private InformationRequest request;
}
