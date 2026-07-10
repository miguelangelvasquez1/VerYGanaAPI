package com.verygana2.services.games;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Pesos del scoring de campañas — análogos a {@code AdScoringConfig} pero para la
 * selección de la campaña más adecuada al iniciar un juego patrocinado.
 */
@Data
@Component
@ConfigurationProperties(prefix = "campaigns.scoring")
public class CampaignScoringConfig {

    /** Peso para coincidencia de categoría (score máximo = este valor). */
    private double categoryMatch = 40.0;

    /** Peso para coincidencia de edad. */
    private double ageMatch = 15.0;

    /** Peso para coincidencia de género. */
    private double genderMatch = 15.0;

    /** Peso para ratio de oportunidad comercial (presupuesto restante / presupuesto total). */
    private double budgetOpportunity = 20.0;

    /** Magnitud de la penalización por repetición reciente (siempre positivo, se aplica como negativo). */
    private double recencyPenalty = 20.0;

    /**
     * Ventana de decaimiento de la penalización de recencia en minutos.
     * La penalización llega a 0 cuando han pasado este número de minutos desde la última sesión.
     * Default: 1 día (los juegos, a diferencia de los anuncios, se repiten con más frecuencia).
     */
    private long recencyDecayWindowMinutes = 24 * 60L;

    /** Número máximo de candidatos elegibles a evaluar en el scorer. */
    private int candidateLimit = 50;
}
