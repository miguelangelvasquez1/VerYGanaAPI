package com.verygana2.models.raffles;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.PrizeStatus;
import com.verygana2.models.enums.raffles.PrizeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffle_prizes", indexes = {
    @Index(name = "idx_raffle_prizes", columnList = "raffle_id"),
    @Index(name = "idx_prize_position", columnList = "raffle_id, position"),
    @Index(name = "idx_prize_status", columnList = "prize_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prize {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    // ========== INFORMACIÓN DEL PREMIO ==========
    
    @NotNull
    @Size(max = 200, message = "Prize title cannot exceed 200 characters")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 100, message = "Brand cannot exceed 100 characters")
    @Column(name = "brand")
    private String brand;

    @NotNull
    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "image_url")
    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "prize_type")
    private PrizeType prizeType; // PHYSICAL, DIGITAL, CASH, VOUCHER

    // ========== POSICIÓN Y CANTIDAD ==========
    
    @NotNull
    @Min(value = 1, message = "Position must be at least 1")
    @Column(name = "position", nullable = false)
    private Integer position; // 1 = primer lugar, 2 = segundo lugar, etc.

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity; // Cantidad de este premio a sortear

    @Column(name = "claimed_count")
    private Integer claimedCount; // Cuántos han sido reclamados

    // ========== ESTADO ==========
    
    @Enumerated(EnumType.STRING)
    @Column(name = "prize_status", nullable = false)
    private PrizeStatus prizeStatus;

    // ========== ENTREGA ==========
    
    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping;

    @Column(name = "estimated_delivery_days")
    private Integer estimatedDeliveryDays;

    @Column(name = "redemption_instructions", columnDefinition = "TEXT")
    private String redemptionInstructions; // Para premios digitales

    // ========== RELACIÓN CON GANADOR ==========
    
    @OneToOne(mappedBy = "prize", fetch = FetchType.LAZY)
    private RaffleWinner winner;

    // ========== AUDITORÍA ==========
    
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    // ========== LIFECYCLE HOOKS ==========
    
    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.createdAt = now;
        this.updatedAt = now;
        this.prizeStatus = PrizeStatus.PENDING;
        this.claimedCount = 0;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    // ========== MÉTODOS DE UTILIDAD ==========
    
    /**
     * Verifica si aún hay premios disponibles para sortear
     */
    public boolean hasAvailablePrizes() {
        return claimedCount < quantity;
    }

    /**
     * Incrementa el contador de premios reclamados
     */
    public void incrementClaimedCount() {
        if (this.claimedCount == null) {
            this.claimedCount = 0;
        }
        this.claimedCount++;
        
        if (this.claimedCount >= this.quantity) {
            this.prizeStatus = PrizeStatus.DELIVERED; // Todos entregados
        }
    }

    /**
     * Obtiene el nombre completo del premio con posición
     */
    public String getFullName() {
        String positionName = switch(position) {
            case 1 -> "1er Lugar";
            case 2 -> "2do Lugar";
            case 3 -> "3er Lugar";
            default -> position + "º Lugar";
        };
        return positionName + " - " + title;
    }
}
