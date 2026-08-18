package com.verygana2.models.pets;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "catalog_integration_requests")
@Data
@NoArgsConstructor
public class CatalogIntegrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "image_object_key", length = 300)
    private String imageObjectKey;

    @Column(name = "desired_effects", nullable = false, length = 1000)
    private String desiredEffects;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogRequestStatus status = CatalogRequestStatus.PENDING;

    /**
     * Diseñador al que el admin asignó la solicitud. Mismo modelo que
     * BrandingRequest.assignedDesigner: hasta que no hay asignación, ningún
     * diseñador la ve en su bandeja.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_designer_id")
    private GameDesignerDetails assignedDesigner;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Borrador del ítem que el diseñador va armando después de aceptar la solicitud.
     * Se guarda parcial (mismo patrón que BrandingRequest.draftFormData) y solo se
     * convierte en un PetCatalogItem real al publicar.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_draft", columnDefinition = "json")
    private Map<String, Object> itemDraft;

    @Column(name = "result_catalog_item_id")
    private Long resultCatalogItemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}