package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;

import lombok.Data;

@Data
public class RuleResponseDTO {
    private Long id; // ✅ ID de la regla
    private String ruleName; // ✅ Nombre descriptivo
    private String description; // ✅ Descripción
    private RuleType ruleType; // ✅ Tipo (PURCHASE, GAME, etc.)
    private boolean isActive; // ✅ Estado activo/inactivo
    private Integer priority;
    private Integer ticketsToAward; // ✅ Cantidad fija de tickets
    private BigDecimal ticketsMultiplier; // ✅ Multiplicador dinámico
    private RaffleType appliesToRaffleType; // ✅ A qué tipo de rifa aplica

    // Para PURCHASE:
    private BigDecimal minPurchaseAmount; // ✅ Monto mínimo
    private String productCategory; // ✅ Categoría requerida

    // Para ADS:
    private Integer minAdsWatched; // ✅ cantidad minima de anuncios vistos

    // Para GAME_ACHIEVEMENT:
    private String achievementType; // ✅ Tipo de logro
    private Integer minAchievementValue; // ✅ Valor mínimo del logro

    // Para REFERRAL:
    private Integer referralAddedQuantity; // ✅ Cuantas personas debe referir el usuario para ganar los tickets

    private Integer maxTicketsPerUserPerDay; // ✅ Límite diario por usuario
    private Integer maxTicketsPerUserTotal; // ✅ Límite total por usuario
    private Long maxUsesGlobal; // ✅ Límite global de la regla
    private Long currentUsesCount; // ✅ Contador actual de usos
    private boolean requiresPet; // ✅ Requiere mascota

    private ZonedDateTime validFrom; // ✅ Fecha inicio
    private ZonedDateTime validUntil; // ✅ Fecha fin

    private ZonedDateTime createdAt; // ✅ Cuándo se creó
    private ZonedDateTime updatedAt; // ✅ Última modificación
}
