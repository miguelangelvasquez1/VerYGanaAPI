package com.verygana2.utils.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuración para el sistema de auditoría
 */
@Configuration
@EnableAspectJAutoProxy
@EnableAsync
public class AuditConfig {

    /**
     * Executor dedicado para auditoría asíncrona
     * Configuración optimizada para no bloquear operaciones principales
     */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool: threads siempre activos
        executor.setCorePoolSize(2);
        
        // Max pool: máximo de threads
        executor.setMaxPoolSize(10);
        
        // Queue capacity: cola de espera
        executor.setQueueCapacity(500);
        
        // Thread name prefix
        executor.setThreadNamePrefix("audit-");
        
        // Qué hacer cuando la cola está llena
        // CallerRunsPolicy: ejecutar en el thread que llama (backpressure)
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Esperar a que terminen las tareas al shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }

    /**
     * ObjectMapper personalizado para serialización de auditoría
     */
    @Bean(name = "auditObjectMapper")
    public ObjectMapper auditObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Registrar módulo para Java 8+ date/time
        mapper.registerModule(new JavaTimeModule());
        
        // No fallar en propiedades desconocidas
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
            false
        );
        
        // Escribir fechas como strings, no timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ignorar nulls en serialización
        mapper.setSerializationInclusion(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
        );
        
        return mapper;
    }
}