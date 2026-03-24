package com.verygana2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // En prod reemplaza con tu dominio frontend
                .withSockJS(); // Fallback para navegadores sin WS nativo
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefijo para mensajes que van del cliente al servidor (@MessageMapping)
        registry.setApplicationDestinationPrefixes("/app");
        
        // Prefijo para canales de broadcast (a los que los clientes se suscriben)
        registry.enableSimpleBroker("/topic", "/queue");
        
        // /topic = broadcast (muchos usuarios)
        // /queue = mensajes individuales (un solo usuario, ej. notificación personal al ganador)
    }
}
