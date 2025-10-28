package com.verygana2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PageableConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer customize() {
        return resolver -> {
            resolver.setOneIndexedParameters(false); // false = el page empieza en 0
            resolver.setMaxPageSize(50);             // límite de size máximo permitido
            resolver.setFallbackPageable(PageRequest.of(0, 10)); // page=0, size=10 por defecto
        };
    }
}   