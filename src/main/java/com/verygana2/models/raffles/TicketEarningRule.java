package com.verygana2.models.raffles;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "ticket_earning_rules", indexes = {
    @Index(name = "idx_active_rules", columnList = "is_active, rule_type"),
    @Index(name = "idx_rule_type", columnList = "rule_type"),
    @Index(name = "idx_priority", columnList = "priority DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketEarningRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(max = 100, message = "Rule name cannot exceed 100 characters")
    @Column(name = "rule_name", nullable = false, unique = true)
    private String ruleName;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType; // PURCHASE, SUBSCRIPTION, GAME_ACHIEVEMENT, REFERRAL, etc.

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "priority", nullable = false)
    private Integer priority; // Mayor número = mayor prioridad

    // ========== CONFIGURACIÓN DE CONDICIONES ==========
    
    // Para PURCHASE (compras)
    @Column(name = "min_purchase_amount", precision = 10, scale = 2)
    private BigDecimal minPurchaseAmount; // Mínimo a comprar
    
    @Column(name = "product_category")
    @Size(max = 100)
    private String productCategory; // null = cualquier categoría, "PREMIUM" = solo premium

    // Para PURCHASE (compras)
    @Column(name = "min_ads_watched")
    private Integer minAdsWatched; // Mínimo a comprar

    // Para GAME_ACHIEVEMENT (logros en juegos)
    @Column(name = "achievement_type")
    @Size(max = 100)
    private String achievementType; // "LEVEL_UP", "PERFECT_GAME", "DAILY_STREAK"
    
    @Column(name = "min_achievement_value")
    private Integer minAchievementValue; // Ej: nivel 10, racha de 7 días

    // Para REFERRAL (referidos)
    
    @Column(name = "referral_added_quantity")
    private Integer referralAddedQuantity; // Ej: por cada persona referida te damos 3 tickets

    // Condiciones genéricas en JSON (para casos especiales)
    @Column(name = "custom_conditions", columnDefinition = "TEXT")
    private String customConditions; // JSON: {"day_of_week": "friday", "hour_range": "18-22"}

    // ========== RECOMPENSA ==========
    
    @NotNull
    @Min(value = 1, message = "Tickets to award must be at least 1")
    @Column(name = "tickets_to_award", nullable = false)
    private Integer ticketsToAward;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to_raffle_type")
    private RaffleType appliesToRaffleType; // PREMIUM, STANDARD, null = ambos

    @Column(name = "tickets_multiplier", precision = 5, scale = 2)
    private BigDecimal ticketsMultiplier; // Para calcular dinámicamente: amount/1000 * multiplier

    // ========== RESTRICCIONES ==========
    
    @Column(name = "max_tickets_per_user_per_day")
    private Integer maxTicketsPerUserPerDay;

    @Column(name = "max_tickets_per_user_total")
    private Integer maxTicketsPerUserTotal; // Por esta regla en total

    @Column(name = "max_uses_global")
    private Long maxUsesGlobal; // Límite global de veces que se puede usar la regla

    @Column(name = "current_uses_count")
    private Long currentUsesCount; // Contador actual de usos

    @Column(name = "requires_pet", nullable = false)
    private boolean requiresPet; // Usuario debe tener mascota registrada

    // ========== VALIDEZ TEMPORAL ==========
    
    @Column(name = "valid_from")
    private ZonedDateTime validFrom;

    @Column(name = "valid_until")
    private ZonedDateTime validUntil;

    // ========== AUDITORÍA ==========
    
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy; // ID del admin que creó la regla

    @Column(name = "last_modified_by")
    private Long lastModifiedBy; // ID del último admin que modificó

    // ========== LIFECYCLE HOOKS ==========
    
    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.createdAt = now;
        this.updatedAt = now;
        this.isActive = true;
        this.priority = 0;
        this.currentUsesCount = 0L;
        this.requiresPet = false;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    // ========== MÉTODOS DE UTILIDAD ==========
    
    /**
     * Verifica si la regla está vigente en la fecha actual
     */
    public boolean isCurrentlyValid() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        return true;
    }

    /**
     * Verifica si aún hay cupo disponible (si tiene límite global)
     */
    public boolean hasGlobalCapacity() {
        if (maxUsesGlobal == null) {
            return true; // Sin límite
        }
        return currentUsesCount < maxUsesGlobal;
    }

    /**
     * Incrementa el contador de usos
     */
    public void incrementUsageCount() {
        if (this.currentUsesCount == null) {
            this.currentUsesCount = 0L;
        }
        this.currentUsesCount++;
    }
}