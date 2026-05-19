package com.verygana2.config.wompi;


import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "wompi")
@Getter
@Setter
public class WompiConfig {
    
    @NotBlank
    private String publicKey;

    @NotBlank
    private String privateKey;

    @NotBlank
    private String integritySecret;

    @NotBlank
    private String eventsKey;     
    
    @NotBlank
    private String apiBaseUrl;

    @NotBlank
    private String checkoutBaseUrl;
}
