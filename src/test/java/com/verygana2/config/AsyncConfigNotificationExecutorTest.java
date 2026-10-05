package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigNotificationExecutorTest {

    /**
     * Con la política por defecto, la tarea que no cabe lanza TaskRejectedException en el
     * hilo que la envía: el service de negocio que quería notificar hacía rollback.
     */
    @Test
    void saturatedExecutorDropsTheNotificationInsteadOfThrowingToTheCaller() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().notificationExecutor();
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocked = () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            int capacity = executor.getMaxPoolSize() + executor.getQueueCapacity();
            for (int i = 0; i < capacity; i++) {
                executor.execute(blocked);
            }

            assertThatCode(() -> executor.execute(blocked)).doesNotThrowAnyException();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
