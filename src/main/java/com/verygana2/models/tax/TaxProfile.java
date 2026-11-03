package com.verygana2.models.tax;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.models.userDetails.CommercialDetails;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Ficha tributaria del empresario (4.7). Es solo la cabecera: el contenido vive en
 * {@link TaxProfileVersion}, una por cada envío, que nunca se modifica.
 *
 * La ficha es información declarada y revisada. No cambia la Vocación Empresarial
 * ({@code CommercialDetails.commercialActivityType}), la comisión ni el plan.
 */
@Entity
@Table(name = "tax_profiles")
@Getter
@Setter
public class TaxProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false, unique = true, updatable = false)
    private CommercialDetails commercial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxProfileStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /** Dos envíos simultáneos no pueden crear el mismo número de versión. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @PrePersist
    protected void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
