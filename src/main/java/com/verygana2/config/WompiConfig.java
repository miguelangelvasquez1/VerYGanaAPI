package com.verygana2.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "wompi")
@Getter
@Setter
public class WompiConfig {
    
    private String publicKey;
    private String privateKey;
    private String eventSecret;
    private String apiUrl;
    private String currency = "COP";
    
    @Bean
    public WebClient wompiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(Objects.requireNonNull(apiUrl, "wompi.apiUrl must be set"))
                .build();
    }
}
