package com.verygana2.models.compliance;

import java.time.LocalDateTime;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

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

@Entity
@Table(name = "screening_results", indexes = {
        @Index(name = "idx_screening_user_id", columnList = "user_id"),
        @Index(name = "idx_screening_status", columnList = "status"),
        @Index(name = "idx_screening_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No FK intencional — puede referir a un userId de un registro que fue bloqueado y nunca persistido
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nombre_consultado", nullable = false, length = 300)
    private String nombreConsultado;

    @Column(name = "documento_consultado", length = 30)
    private String documentoConsultado;

    @Enumerated(EnumType.STRING)
    @Column(name = "lista", nullable = false, length = 30)
    private ScreeningList lista;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScreeningStatus status;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "reviewed_by_officer_id")
    private Long reviewedByOfficerId;

    @Column(name = "officer_notes", length = 500)
    private String officerNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}