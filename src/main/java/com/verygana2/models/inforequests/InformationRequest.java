package com.verygana2.models.inforequests;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.userDetails.CommercialDetails;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Requerimiento de actualización o aclaración al empresario (4.7 / 4.8). Dice qué se pide,
 * por qué y con qué regla, y conserva comunicación, respuestas y revisiones. Pedir
 * información nunca suspende ni sanciona: eso solo sale de un {@link ComplianceFinding}.
 */
@Entity
@Table(name = "information_requests", indexes = {
        @Index(name = "idx_info_request_commercial_status", columnList = "commercial_id, status")
})
@Getter
@Setter
public class InformationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false, updatable = false)
    private CommercialDetails commercial;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 40, updatable = false)
    private InformationRequestTrigger trigger;

    /** Regla automática que lo originó, con su versión. Null si lo emitió una persona. */
    @Column(name = "rule_code", length = 50, updatable = false)
    private String ruleCode;

    @Column(name = "rule_version", updatable = false)
    private Integer ruleVersion;

    /** Motivo concreto. */
    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    /** Qué requisito de la vinculación o del funcionamiento se evalúa con lo pedido. */
    @Column(name = "relation_to_operation", nullable = false, length = 1000, updatable = false)
    private String relationToOperation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private InformationRequestStatus status;

    /** Plazo, solo cuando una regla aplicable lo fija. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Quién lo emitió. Null = el sistema, por una regla o un evento. */
    @Column(name = "issued_by_user_id", updatable = false)
    private Long issuedByUserId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private ZonedDateTime issuedAt;

    /** Identifica "el mismo requerimiento": no se abren dos iguales por reintentos. */
    @Column(name = "dedup_key", nullable = false, length = 150, updatable = false)
    private String dedupKey;

    /** Igual a {@code dedupKey} mientras está abierto, null al cerrarse. Único en la tabla. */
    @Column(name = "open_dedup_key", length = 150, unique = true)
    private String openDedupKey;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Column(name = "closing_notes", length = 1000)
    private String closingNotes;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<InformationRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<InformationRequestCommunication> communications = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<InformationRequestResponse> responses = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<InformationRequestReview> reviews = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (issuedAt == null) {
            issuedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = InformationRequestStatus.OPEN;
        }
    }

    public void close(InformationRequestStatus closedStatus, Long userId, String notes) {
        this.status = closedStatus;
        this.closedAt = ZonedDateTime.now();
        this.closedByUserId = userId;
        this.closingNotes = notes;
        this.openDedupKey = null;
    }
}
