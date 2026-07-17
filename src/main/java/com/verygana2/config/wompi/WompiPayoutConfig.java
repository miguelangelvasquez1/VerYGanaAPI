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
        private String cron = "0 0 4 * * *";
        private String retryCron = "0 30 4 * * *";
        private long minBalanceAlertCents = 5_000_000L;
    }
}
