package com.verygana2.models.raffles;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;

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
import jakarta.persistence.OneToMany;
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

    @OneToMany(mappedBy = "ticketEarningRule", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaffleRule> raffleRules;

    @NotNull
    @Size(max = 100, message = "Rule name cannot exceed 100 characters")
    @Column(name = "rule_name", nullable = false, unique = true)
    private String ruleName;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private TicketEarningRuleType ruleType; // PURCHASE, GAME_ACHIEVEMENT, REFERRAL, etc.

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "priority", nullable = false)
    private Integer priority; // Mayor número = mayor prioridad

    // ========== CONFIGURACIÓN DE CONDICIONES ==========
    
    @Column(name = "min_purchase_amount", precision = 10, scale = 2)
    private BigDecimal minPurchaseAmount; // Mínimo a comprar

    @Column(name = "min_ads_watched")
    private Integer minAdsWatched; // Mínimo a visualizar

    @Column(name = "achievement_type")
    @Size(max = 100)
    private String achievementType; // "LEVEL_UP", "PERFECT_GAME", "DAILY_STREAK"

    @Column(name = "referral_added_quantity")
    private Integer referralAddedQuantity; // Ej: por cada persona referida te damos 3 tickets

    // ========== RECOMPENSA ==========
    
    @NotNull
    @Min(value = 1, message = "Tickets to award must be at least 1")
    @Column(name = "tickets_to_award", nullable = false)
    private Integer ticketsToAward;

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
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }


}