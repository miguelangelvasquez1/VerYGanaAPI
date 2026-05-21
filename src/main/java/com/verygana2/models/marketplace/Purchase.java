package com.verygana2.models.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "purchases", indexes = {
        @Index(name = "idx_consumer_id", columnList = "consumer_id"),
        @Index(name = "idx_purchase_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "consumer", "items" })
@EqualsAndHashCode(exclude = { "consumer", "items" })
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, updatable = false, unique = true)
    private String referenceId;

    // ===== RELACIONES =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();  
    
    // ===== SNAPSHOT FINANCIERO =====
    // Todos estos campos se calculan UNA SOLA VEZ en PurchaseService.create()
    // y no vuelven a cambiar. Son el registro histórico de lo que ocurrió.
 
    /**
     * Precio total de todos los ítems en centavos de COP.
     * = suma de (PurchaseItem.unitPriceCents × quantity) por cada ítem.
     */
    @Column(name = "total_cents", nullable = false)
    @Builder.Default
    private Long totalCents = 0L;

    /**
     * Cuántos centavos de ese total pagó el usuario con llaves.
     * = keysUsed × 1.000 (1 llave = $10 COP = 1.000 centavos).
     * Se llena cuando el Copayment asociado pasa a COMPLETED.
     */
    @Column(name = "keys_value_cents", nullable = false)
    @Builder.Default
    private Long keysValueCents = 0L;

    /**
     * Cuántos centavos pagó el usuario con dinero real vía Wompi.
     * = totalCents - keysValueCents.
     * Se llena cuando el Copayment asociado pasa a COMPLETED.
     */
    @Column(name = "cash_cents", nullable = false)
    @Builder.Default
    private Long cashCents = 0L;

    /**
     * Comisión retenida por VeryGana sobre esta compra, en centavos.
     * Calculada al momento de crear la Purchase según el plan del empresario
     * y si su commissionActive = true o false.
     * Se persiste como snapshot para auditoría histórica: si la tasa cambia
     * en el futuro, este registro muestra lo que se cobró realmente.
     */
    @Column(name = "commission_cents", nullable = false)
    @Builder.Default
    private Long commissionCents = 0L;

    /**
     * Suma de PurchaseItem.netToCommercialCents de todos los ítems.
     * = totalCents - commissionCents
     * Lo que en conjunto se les debe pagar a todos los vendedores de esta compra.
     */
    @Column(name = "net_to_commercials_cents", nullable = false)
    @Builder.Default
    private Long netToCommercialsCents = 0L;

    // ===== ESTADO DE LA COMPRA =====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.PENDING;

    // ===== INFORMACIÓN DE ENTREGA DIGITAL =====

    /**
     * Email al que se enviarán los códigos o accesos del producto digital.
     * Puede ser el mismo email de registro del usuario u otro si el usuario lo desea.
     */
    @Column(name = "delivery_email", length = 255)
    private String deliveryEmail; // puede ser el email registrado u otro verificado durante la compra

    /**
     * Si el usuario elije recibir los productos a otro correo este necesita ser verificado.
     */
    @Column(name = "delivery_email_verified", nullable = false)
    @Builder.Default
    private boolean deliveryEmailVerified = false;

    // ===== AUDITORÍA =====

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt; // Cuándo se completó la compra

    // ===== MÉTODOS DE CICLO DE VIDA =====

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ===== MÉTODOS DE NEGOCIO =====

    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchase(this);
    }

    public void removeItem(PurchaseItem item) {
        items.remove(item);
        item.setPurchase(null);
    }

    /**
     * Calcula y persiste todos los campos financieros a partir de los ítems.
     * Se llama UNA SOLA VEZ en PurchaseService.create() después de agregar
     * todos los ítems. Nunca se vuelve a llamar.
     */
    public void calculateFinancials() {
        this.totalCents = items.stream()
                .mapToLong(PurchaseItem::getSubtotalCents)
                .sum();
        this.commissionCents = items.stream()
                .mapToLong(PurchaseItem::getCommissionCents)
                .sum();
        this.netToCommercialsCents = items.stream()
                .mapToLong(PurchaseItem::getNetToCommercialCents)
                .sum();
        // keysValueCents y cashCents se asignan externamente
        // cuando el Copayment confirma cuánto pagó con llaves si aplica y cuánto con Wompi.
    }

    public void markAsCompleted() {
        this.status = PurchaseStatus.COMPLETED;
        this.completedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}