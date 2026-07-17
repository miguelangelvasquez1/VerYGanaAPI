package com.verygana2.models.legal;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentType;

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
import lombok.Data;

/**
 * Versión de un documento legal (Términos y Condiciones, Política de Privacidad,
 * etc.). El PDF se sube directamente desde el frontend a R2 vía URL pre-firmada
 * (mismo flujo que los assets de anuncios) — aquí se guarda la referencia (URL) y
 * metadata de versión, para que el backend sea la fuente única de verdad.
 */
@Entity
@Table(name = "legal_documents", indexes = {
        @Index(name = "idx_legal_documents_type_active", columnList = "type, active")
})
@Data
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LegalDocumentType type;

    @Column(nullable = false, length = 20)
    private String version;

    /** URL pública del CDN — calculada de forma determinística a partir de objectKey al preparar la subida. */
    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;

    /** Clave del objeto en R2 (bajo public/), sin el prefijo "public/". */
    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LegalDocumentStatus status;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    /** Solo una versión VALIDATED activa por tipo — ver LegalDocumentServiceImpl.confirmUpload(). */
    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = LegalDocumentStatus.PENDING;
        }
    }
}
