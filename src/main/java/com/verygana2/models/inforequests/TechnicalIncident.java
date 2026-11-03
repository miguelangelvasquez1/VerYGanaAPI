package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Falla de interfaz o de transmisión. Se registra aparte para que nunca se confunda con una
 * negativa o un incumplimiento deliberado.
 */
@Entity
@Table(name = "technical_incidents")
@Getter
@Setter
public class TechnicalIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    /** Dónde ocurrió (p. ej. "CONTRACT_SHEET_SECTION_9", "DOCUMENT_UPLOAD"). */
    @Column(nullable = false, length = 100)
    private String context;

    @Column(nullable = false, length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private InformationRequest request;

    @Column(name = "manifestation_id")
    private Long manifestationId;

    /** Null = lo registró el sistema. */
    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;
}
