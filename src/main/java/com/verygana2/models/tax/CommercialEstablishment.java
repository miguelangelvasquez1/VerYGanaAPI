package com.verygana2.models.tax;

import java.time.ZonedDateTime;

import com.verygana2.models.Municipality;
import com.verygana2.models.enums.tax.EstablishmentRelation;
import com.verygana2.models.enums.tax.EstablishmentStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Establecimiento físico del empresario (inciso f). El número de establecimientos se
 * calcula desde estos registros. Un canal digital, un contacto o una oferta no es un
 * establecimiento. No se borran: se cierran, para conservar el historial.
 */
@Entity
@Table(name = "commercial_establishments", indexes = {
        @Index(name = "idx_establishment_commercial_status", columnList = "commercial_id, status")
})
@Getter
@Setter
public class CommercialEstablishment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false, updatable = false)
    private CommercialDetails commercial;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    /** Dirección en minúsculas, sin tildes ni signos: detecta la misma sede registrada dos veces. */
    @Column(name = "address_normalized", nullable = false, length = 300)
    private String addressNormalized;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "municipality_code", nullable = false)
    private Municipality municipality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstablishmentRelation relation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstablishmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @Column(name = "closed_reason", length = 300)
    private String closedReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = EstablishmentStatus.ACTIVE;
        }
    }
}
