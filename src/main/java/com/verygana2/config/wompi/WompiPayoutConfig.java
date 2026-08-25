package com.verygana2.config.wompi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "wompi.payout")
@Getter
@Setter
public class WompiPayoutConfig {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String principalUserId;

    @NotBlank
    private String userId;

    @NotBlank
    private String apiBaseUrl;

    @NotBlank
    private String eventsKey;

    /** Identificador de la cuenta de origen de las dispersiones (GET /accounts). */
    @NotBlank
    private String accountId;

    /** bankId (UUID) del catálogo /banks de Wompi que representa a Nequi. */
    @NotBlank
    private String nequiBankId;

    /** bankId (UUID) del catálogo /banks de Wompi que representa a Daviplata. */
    @NotBlank
    private String daviplataBankId;

    private Payout payout = new Payout();

    @Getter
    @Setter
    public static class Payout {
        private String cron = "0 0 23 * * *";
        private String retryCron = "0 30 23 * * *";
        private long minBalanceAlertCents = 5_000_000L;

        /** Timeout máximo para establecer la conexión TCP con Wompi. */
        private int connectTimeoutMs = 5_000;

        /** Timeout máximo esperando la respuesta completa de Wompi tras enviar el request. */
        private int responseTimeoutMs = 20_000;

        /**
         * Máximo de reintentos antes de marcar el Payout como EXHAUSTED y dejar
         * de reintentarlo automáticamente. Sin este tope, un payout con una
         * falla estructural (ej. cuenta bancaria mal cargada) se reintentaría
         * cada noche indefinidamente.
         */
        private int maxRetries = 5;

        /**
         * Pausa entre llamadas consecutivas a POST /payouts dentro de un mismo
         * batch (~100 payouts seguidos). Sin esto, Wompi puede empezar a
         * responder 429 a mitad de la corrida si tiene un límite de requests
         * por segundo/minuto.
         */
        private long rateLimitDelayMs = 300L;
    }
}
