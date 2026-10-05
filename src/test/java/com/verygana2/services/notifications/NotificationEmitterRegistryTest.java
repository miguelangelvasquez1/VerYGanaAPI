package com.verygana2.services.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;

class NotificationEmitterRegistryTest {

    private static final Long USER_ID = 7L;

    private final List<FakeEmitter> created = new ArrayList<>();
    private NotificationEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationEmitterRegistry() {
            @Override
            protected SseEmitter newEmitter() {
                FakeEmitter emitter = new FakeEmitter();
                created.add(emitter);
                return emitter;
            }
        };
    }

    /**
     * El bug: al completarse una conexión vieja, su callback borraba la entrada del
     * usuario entera y se llevaba a la conexión nueva, que quedaba abierta sin recibir nada.
     */
    @Test
    void completingAnOlderConnectionKeepsTheNewerOneRegistered() {
        FakeEmitter first = register();
        FakeEmitter second = register();

        first.fireCompletion();

        assertThat(registry.isConnected(USER_ID)).isTrue();
        int before = second.sent;
        registry.send(USER_ID, new NotificationResponseDTO());
        assertThat(second.sent).isEqualTo(before + 1);
    }

    @Test
    void timeoutOfOneConnectionDoesNotRemoveTheOthers() {
        FakeEmitter first = register();
        register();

        first.fireTimeout();

        assertThat(first.completed).isTrue();
        assertThat(registry.connectionCount(USER_ID)).isEqualTo(1);
    }

    @Test
    void errorOfOneConnectionDoesNotRemoveTheOthers() {
        FakeEmitter first = register();
        register();

        first.fireError();

        assertThat(registry.connectionCount(USER_ID)).isEqualTo(1);
    }

    @Test
    void notificationReachesEveryOpenConnectionOfTheUser() {
        FakeEmitter first = register();
        FakeEmitter second = register();
        int firstBefore = first.sent;
        int secondBefore = second.sent;

        registry.send(USER_ID, new NotificationResponseDTO());

        assertThat(first.sent).isEqualTo(firstBefore + 1);
        assertThat(second.sent).isEqualTo(secondBefore + 1);
    }

    @Test
    void exceedingTheCapClosesTheOldestConnection() {
        FakeEmitter oldest = register();
        for (int i = 0; i < NotificationEmitterRegistry.MAX_EMITTERS_PER_USER; i++) {
            register();
        }

        assertThat(oldest.completed).isTrue();
        assertThat(registry.connectionCount(USER_ID)).isEqualTo(NotificationEmitterRegistry.MAX_EMITTERS_PER_USER);
    }

    @Test
    void lastConnectionClosingLeavesTheUserDisconnected() {
        FakeEmitter only = register();

        only.fireCompletion();

        assertThat(registry.isConnected(USER_ID)).isFalse();
    }

    @Test
    void sendToAnAlreadyCompletedEmitterRemovesItWithoutPropagating() {
        FakeEmitter dead = register();
        FakeEmitter alive = register();
        dead.failure = new IllegalStateException("ResponseBodyEmitter has already completed");

        assertThatCode(() -> registry.send(USER_ID, new NotificationResponseDTO())).doesNotThrowAnyException();

        assertThat(registry.connectionCount(USER_ID)).isEqualTo(1);
        assertThat(alive.sent).isGreaterThan(0);
    }

    @Test
    void heartbeatDropsConnectionsTheClientAlreadyClosed() {
        FakeEmitter gone = register();
        gone.failure = new IOException("Broken pipe");

        registry.heartbeat();

        assertThat(registry.isConnected(USER_ID)).isFalse();
    }

    private FakeEmitter register() {
        return (FakeEmitter) registry.register(USER_ID);
    }

    /** Emitter sin servlet detrás: guarda los callbacks para dispararlos desde el test. */
    private static class FakeEmitter extends SseEmitter {
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private Exception failure;
        private int sent;
        private boolean completed;

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            sent++;
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }

        void fireCompletion() {
            completionCallback.run();
        }

        void fireTimeout() {
            timeoutCallback.run();
        }

        void fireError() {
            errorCallback.accept(new IOException("connection reset"));
        }
    }
}
