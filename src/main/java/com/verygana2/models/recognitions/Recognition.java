package com.verygana2.models.recognitions;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.models.marketplace.Purchase;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reconocimiento que un admin le otorga a un usuario destacado (Contrato B 10.16): VERYGANA
 * compra un producto con recursos del Programa de Fortalecimiento y el usuario lo recibe
 * sin pagar dinero ni llaves.
 *
 * Asignación, compra y entrega quedan vinculadas y cada una conserva su estado: el del
 * reconocimiento (aquí), el de la compra ({@link Purchase#getStatus()}) y el de cada ítem.
 * El beneficiario vive en la compra ({@link Purchase#getBeneficiary()}), separado del
 * comprador.
 */
@Entity
@Table(name = "recognitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recognition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true, updatable = false)
    private Purchase purchase;

    /**
     * Identidad del recurso del fondo que paga la compra: es el referenceId de los
     * movimientos de tesorería y el sourceRef de los componentes de venta. Seguirlo de punta
     * a punta es lo que impide sumar presupuesto y desembolso como dos aportes.
     */
    @Column(name = "funding_ref", nullable = false, unique = true, updatable = false)
    private UUID fundingRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_criterion", nullable = false, updatable = false, length = 30)
    private RecognitionCriterion selectionCriterion;

    @Column(name = "selection_metric_value", updatable = false)
    private Long selectionMetricValue;

    @Column(name = "selection_period_from", updatable = false)
    private ZonedDateTime selectionPeriodFrom;

    @Column(name = "selection_period_to", updatable = false)
    private ZonedDateTime selectionPeriodTo;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "created_by_admin_id", nullable = false, updatable = false)
    private Long createdByAdminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecognitionStatus status;

    @Column(name = "cancel_cause", length = 500)
    private String cancelCause;

    @Column(name = "cancelled_by_admin_id")
    private Long cancelledByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;

    /** Cuándo se canceló o venció. */
    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @Version
    private long version;

    @Builder
    private Recognition(Purchase purchase, UUID fundingRef, RecognitionCriterion selectionCriterion,
            Long selectionMetricValue, ZonedDateTime selectionPeriodFrom, ZonedDateTime selectionPeriodTo,
            String reason, Long createdByAdminId) {
        this.purchase = purchase;
        this.fundingRef = fundingRef;
        this.selectionCriterion = selectionCriterion;
        this.selectionMetricValue = selectionMetricValue;
        this.selectionPeriodFrom = selectionPeriodFrom;
        this.selectionPeriodTo = selectionPeriodTo;
        this.reason = reason;
        this.createdByAdminId = createdByAdminId;
        this.status = RecognitionStatus.PURCHASED;
    }

    /** Idempotente: una segunda confirmación de entrega no cambia nada. */
    public void markDelivered() {
        if (status == RecognitionStatus.DELIVERED) {
            return;
        }
        requirePurchased();
        this.status = RecognitionStatus.DELIVERED;
        this.deliveredAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void markExpired() {
        requirePurchased();
        this.status = RecognitionStatus.EXPIRED;
        this.closedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void cancel(String cause, Long adminId) {
        if (status == RecognitionStatus.CANCELLED || status == RecognitionStatus.EXPIRED) {
            throw new InvalidStatusException("El reconocimiento ya está cerrado: " + status);
        }
        this.status = RecognitionStatus.CANCELLED;
        this.cancelCause = cause;
        this.cancelledByAdminId = adminId;
        this.closedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    private void requirePurchased() {
        if (status != RecognitionStatus.PURCHASED) {
            throw new InvalidStatusException("El reconocimiento no está en PURCHASED: " + status);
        }
    }

    @PrePersist
    void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
