package com.verygana2.models.commercial;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.enums.commercial.CommercialDocumentType;

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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** Paso 8 - Carga documental (RUT, Cámara de Comercio, cédula, certificación bancaria, etc.). */
@Entity
@Table(name = "commercial_documents", indexes = {
        @Index(name = "idx_commercial_documents_onboarding", columnList = "commercial_onboarding_id")
})
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = { "onboarding", "commercial", "replacesDocument" })
public class CommercialDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_onboarding_id", nullable = false)
    private CommercialOnboarding onboarding;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private CommercialDocumentType documentType;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", length = 50)
    private SupportedMimeType mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommercialDocumentStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private ZonedDateTime uploadedAt;

    // ==================== EXPEDIENTE (4.7 / 4.8) ====================

    /**
     * Empresario dueño del documento. El documento vive en su expediente, no solo en el
     * onboarding: las actualizaciones posteriores también cuelgan de aquí.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id")
    private CommercialDetails commercial;

    /** Documento que esta versión reemplaza (la anterior queda SUPERSEDED, sin borrarse). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_document_id")
    private CommercialDocument replacesDocument;

    @Column(name = "superseded_at")
    private ZonedDateTime supersededAt;

    /** Fecha de expedición comprobada en la revisión. */
    @Column(name = "issued_at")
    private LocalDate issuedAt;

    /**
     * Fin de vigencia comprobado en la revisión. Null si el documento no tiene una vigencia
     * aplicable: en ese caso nunca se considera vencido.
     */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** NIT o cédula del titular que figura en el documento, tal como lo leyó el revisor. */
    @Column(name = "holder_identification", length = 30)
    private String holderIdentification;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    private CommercialDocumentReviewStatus reviewStatus;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = CommercialDocumentStatus.PENDING;
        }
        if (reviewStatus == null) {
            reviewStatus = CommercialDocumentReviewStatus.NOT_REVIEWED;
        }
    }
}
