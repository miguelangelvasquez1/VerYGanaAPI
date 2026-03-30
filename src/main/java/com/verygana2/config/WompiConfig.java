package com.verygana2.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.context.annotation.Configuration;


import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "wompi")
@Getter
@Setter
public class WompiConfig {
    
    private String publicKey;
    private String privateKey;
    private String integrityKey;
    private String eventsKey;        // Para validar webhooks
    private String checkoutBaseUrl;
    private String apiBaseUrl;
}
