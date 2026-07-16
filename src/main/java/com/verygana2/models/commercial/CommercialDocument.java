package com.verygana2.models.commercial;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
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

/** Paso 8 - Carga documental (RUT, Cámara de Comercio, cédula, certificación bancaria, etc.). */
@Entity
@Table(name = "commercial_documents", indexes = {
        @Index(name = "idx_commercial_documents_onboarding", columnList = "commercial_onboarding_id")
})
@Data
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

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = CommercialDocumentStatus.PENDING;
        }
    }
}
