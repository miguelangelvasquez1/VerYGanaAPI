package com.verygana2.services.ads;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ads.scoring")
public class AdScoringConfig {

    /** Peso para coincidencia de categoría (score máximo = este valor). */
    private double categoryMatch = 40.0;

    /** Peso para coincidencia de edad. */
    private double ageMatch = 15.0;

    /** Peso para coincidencia de género. */
    private double genderMatch = 15.0;

    /** Peso para ratio de oportunidad comercial (remainingLikes / maxLikes). */
    private double opportunityRatio = 20.0;

    /** Magnitud de la penalización por repetición reciente (siempre positivo, se aplica como negativo). */
    private double recencyPenalty = 20.0;

    /**
     * Ventana de decaimiento de la penalización de recencia en minutos.
     * La penalización llega a 0 cuando han pasado este número de minutos desde la última vista.
     * Default: 7 días.
     */
    private long recencyDecayWindowMinutes = 7 * 24 * 60L;

    /** Número máximo de candidatos elegibles a evaluar en el scorer. */
    private int candidateLimit = 50;
}
