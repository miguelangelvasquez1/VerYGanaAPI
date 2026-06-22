// src/main/java/com/verygana2/models/enums/UserLevel.java
package com.verygana2.models.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Niveles del sistema de gamificación.
 * Toda la lógica de negocio del documento v3.0 vive aquí.
 * Los valores base de llaves/XP se multiplican en LevelService.
 */
@Getter
@RequiredArgsConstructor
public enum UserLevel {

    //            xpMin   xpMax    mult  refKeys  refTickets  raffleTickets
    BRONCE  (     0,    999,  0.5,   100,    1,    1),
    PLATA   (  1000,   3999,  0.6,   160,    2,    2),
    ORO     (  4000,   8999,  0.7,   240,    3,    3),
    RUBI    (  9000,  17999,  0.8,   320,    4,    4),
    ESMERALDA(18000,  34999,  0.9,   430,    5,    5),
    DIAMANTE (35000, Long.MAX_VALUE, 1.0, 600, 7,  7);

    private final long xpMin;
    private final long xpMax;
    private final double multiplier;

    /** Llaves que recibe el referidor cuando NO hay rifa activa */
    private final int referralKeys;

    /** Tickets que recibe el referidor cuando HAY rifa activa */
    private final int referralTickets;

    /** Tickets de rifa que puede comprar/usar por nivel */
    private final int raffleTickets;

    // ─── Lógica de negocio ────────────────────────────────────────────────────

    /**
     * Calcula el nivel correspondiente a un XP total acumulado.
     * Itera de mayor a menor para retornar el nivel correcto.
     */
    public static UserLevel fromXp(long xpTotal) {
        UserLevel[] levels = values();
        for (int i = levels.length - 1; i >= 0; i--) {
            if (xpTotal >= levels[i].xpMin) {
                return levels[i];
            }
        }
        return BRONCE;
    }

    /**
     * Aplica el multiplicador del nivel a un valor base.
     * Ejemplo: 100 llaves base * 0.7 (Oro) = 70 llaves reales.
     * Usa Math.round para evitar fracciones de llaves.
     */
    public long applyMultiplier(long baseValue) {
        return Math.round(baseValue * this.multiplier);
    }

    /**
     * Verifica si este nivel puede acceder a rifas de un nivel requerido.
     * Los niveles superiores acumulan acceso (Rubi puede en Bronce, Plata y Oro).
     */
    public boolean canAccessRaffle(UserLevel requiredLevel) {
        return this.ordinal() >= requiredLevel.ordinal();
    }

    /**
     * XP necesario para subir al siguiente nivel.
     * Retorna 0 si ya es Diamante (nivel máximo).
     */
    public long xpToNextLevel(long currentXp) {
        if (this == DIAMANTE) return 0;
        UserLevel[] levels = values();
        return levels[this.ordinal() + 1].xpMin - currentXp;
    }
}