package com.verygana2.models.enums.recognitions;

/** Por qué el admin eligió al beneficiario. Se guarda con el valor de la métrica. */
public enum RecognitionCriterion {
    /** Llaves ganadas por interacción y referidos en el periodo. */
    KEYS_EARNED,
    /** Compras completadas en el periodo. */
    PURCHASES_COUNT,
    /** Valor de las compras completadas en el periodo, en centavos. */
    PURCHASES_AMOUNT,
    /** Partidas jugadas en el periodo. */
    GAMES_PLAYED,
    /** XP acumulado (sin periodo). */
    XP,
    OTHER
}
