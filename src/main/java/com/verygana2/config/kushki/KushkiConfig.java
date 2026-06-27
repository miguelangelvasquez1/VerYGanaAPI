package com.verygana2.config.kushki;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "kushki")
@Getter
@Setter
public class KushkiConfig {

    @NotBlank
    private String privateKey;

    @NotBlank
    private String publicKey;

    @NotBlank
    private String apiBaseUrl;

    private Payout payout = new Payout();

    @Getter
    @Setter
    public static class Payout {
        private String cron = "0 0 4 * * *";
        private String retryCron = "0 30 4 * * *";
        private long minBalanceAlertCents = 5_000_000L;
    }
}
