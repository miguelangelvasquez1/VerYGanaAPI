package com.verygana2.models.pqrs;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.User;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.enums.pqrs.PqrsResolutionAction;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.AdminDetails;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "pqrs",
    indexes = {
        @Index(name = "idx_pqrs_requester", columnList = "requester_id"),
        @Index(name = "idx_pqrs_status", columnList = "status"),
        @Index(name = "idx_pqrs_assigned_admin", columnList = "assigned_admin_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pqrs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PqrsType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PqrsStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private AdminDetails assignedAdmin;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(name = "due_date", nullable = false)
    private ZonedDateTime dueDate;

    /**
     * Nullable: solo se llena cuando el PQRS se radica desde
     * /purchaseItems/{id}/report (ver PqrsService.createPqrsForPurchaseItem).
     * Un PQRS genérico (radicado desde /pqrs) no tiene ítem vinculado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id")
    private PurchaseItem purchaseItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    private MarketplaceIssueReason reasonCode;

    /**
     * Nullable: solo se llena al resolver un PQRS con purchaseItem vinculado
     * (ver PqrsServiceImpl.respondToPqrs). Persistido —no solo el campo
     * transitorio del request— para que el frontend sepa, leyendo el PQRS ya
     * resuelto, si el admin aprobó REFUND y debe mostrarle al comprador el
     * formulario para indicar su cuenta bancaria.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private PqrsResolutionAction action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    /**
     * Evidencia (foto/video) opcional adjuntada por el solicitante — ver
     * PqrsAssetServiceImpl.validateAndClaimAssets. @Builder.Default es
     * obligatorio: sin él, cada Pqrs.builder()...build() dejaría la colección
     * en null (Pqrs usa @Data @Builder, no @Getter/@Setter con inicializador
     * "vivo" fuera del builder).
     */
    @OneToMany(mappedBy = "pqrs", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PqrsAsset> assets = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = assignedAdmin != null ? PqrsStatus.RECIBIDA : PqrsStatus.PENDIENTE_ASIGNACION;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public boolean canBeReviewed() {
        return status == PqrsStatus.RECIBIDA;
    }

    public boolean canBeResolved() {
        return status == PqrsStatus.RECIBIDA || status == PqrsStatus.EN_REVISION;
    }

    // No se persiste: se deriva del id (asignado solo tras el primer save) y el año de creación.
    public String getBased() {
        if (id == null) return null;
        int year = createdAt != null ? createdAt.getYear() : ZonedDateTime.now().getYear();
        return "PQRS-" + year + "-" + String.format("%06d", id);
    }
}
