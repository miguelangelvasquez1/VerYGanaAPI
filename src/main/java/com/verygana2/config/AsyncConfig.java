package com.verygana2.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }

    /**
     * Executor dedicado para las revelaciones de ganadores.
     * Pool pequeño porque los sorteos no son concurrentes normalmente,
     * pero soporta hasta 5 sorteos simultáneos sin problemas.
     */
    @Bean(name = "drawRevealExecutor")
    public Executor drawRevealExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("draw-reveal-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // No cortar revelaciones en medio
        executor.setAwaitTerminationSeconds(60);            // Esperar hasta 60s al apagar
        executor.initialize();
        return executor;
    }
}
