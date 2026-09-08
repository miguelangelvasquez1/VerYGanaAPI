package com.verygana2.services.raffles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.DrawStatusResponseDTO;
import com.verygana2.dtos.raffle.websocket.WinnerRevealPayloadDTO;
import com.verygana2.models.enums.raffles.DrawEventType;
import com.verygana2.models.raffles.Raffle;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RaffleDrawStateCache")
class RaffleDrawStateCacheTest {

    private RaffleDrawStateCache cache;

    @BeforeEach
    void setUp() {
        cache = new RaffleDrawStateCache();
    }

    private WinnerRevealPayloadDTO winner(int position) {
        return WinnerRevealPayloadDTO.builder().position(position).build();
    }

    @Nested
    @DisplayName("onDrawingStarted")
    class OnDrawingStarted {

        @Test
        @DisplayName("reemplaza completamente el estado: fase DRAWING_STARTED y ganadores vacíos")
        void replacesState() {
            cache.onDrawingStarted(1L, 3);

            DrawStatusResponseDTO status = cache.buildStatus(1L, 0, 10L, null, 100L);

            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.DRAWING_STARTED);
            assertThat(status.getRevealedWinners()).isEmpty();
            assertThat(status.getTotalWinners()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("onWinnerRevealed")
    class OnWinnerRevealed {

        @Test
        @DisplayName("con estado previo: agrega el ganador y cambia la fase a WINNER_REVEALED")
        void withPriorState_addsWinnerAndChangesPhase() {
            cache.onDrawingStarted(1L, 2);

            cache.onWinnerRevealed(1L, winner(1));

            DrawStatusResponseDTO status = cache.buildStatus(1L, 0, null, null, 0L);
            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.WINNER_REVEALED);
            assertThat(status.getRevealedWinners()).hasSize(1);
        }

        @Test
        @DisplayName("sin estado previo (no se llamó onDrawingStarted): no-op silencioso, sin excepción")
        void withoutPriorState_isSilentNoOp() {
            cache.onWinnerRevealed(99L, winner(1));

            assertThat(cache.exists(99L)).isFalse();
        }
    }

    @Nested
    @DisplayName("onDrawCompleted")
    class OnDrawCompleted {

        @Test
        @DisplayName("con estado previo: cambia la fase a DRAW_COMPLETED")
        void withPriorState_changesPhase() {
            cache.onDrawingStarted(1L, 1);

            cache.onDrawCompleted(1L);

            DrawStatusResponseDTO status = cache.buildStatus(1L, 0, null, null, 0L);
            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.DRAW_COMPLETED);
        }

        @Test
        @DisplayName("sin estado previo: no-op silencioso, sin excepción")
        void withoutPriorState_isSilentNoOp() {
            cache.onDrawCompleted(99L);

            assertThat(cache.exists(99L)).isFalse();
        }
    }

    @Nested
    @DisplayName("onDrawError")
    class OnDrawError {

        @Test
        @DisplayName("con estado previo: cambia la fase a DRAW_ERROR")
        void withPriorState_changesPhase() {
            cache.onDrawingStarted(1L, 1);

            cache.onDrawError(1L);

            DrawStatusResponseDTO status = cache.buildStatus(1L, 0, null, null, 0L);
            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.DRAW_ERROR);
        }

        @Test
        @DisplayName("sin estado previo: no-op silencioso, sin excepción")
        void withoutPriorState_isSilentNoOp() {
            cache.onDrawError(99L);

            assertThat(cache.exists(99L)).isFalse();
        }
    }

    @Nested
    @DisplayName("evict / exists")
    class EvictExists {

        @Test
        @DisplayName("exists refleja si hay estado en cache; evict lo elimina")
        void evictRemovesState() {
            assertThat(cache.exists(1L)).isFalse();

            cache.onDrawingStarted(1L, 1);
            assertThat(cache.exists(1L)).isTrue();

            cache.evict(1L);
            assertThat(cache.exists(1L)).isFalse();
        }

        @Test
        @DisplayName("evict sobre una rifa sin estado no lanza excepción")
        void evictWithoutState_doesNotThrow() {
            cache.evict(999L);

            assertThat(cache.exists(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("buildStatus")
    class BuildStatus {

        @Test
        @DisplayName("sin estado en cache: WAITING_ROOM_UPDATE, sin ganadores, totalWinners=0, totalParticipants = el parámetro pasado")
        void withoutState_returnsWaitingRoom() {
            DrawStatusResponseDTO status = cache.buildStatus(1L, 5, 30L, null, 250L);

            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.WAITING_ROOM_UPDATE);
            assertThat(status.getRevealedWinners()).isEmpty();
            assertThat(status.getTotalWinners()).isZero();
            assertThat(status.getTotalParticipants()).isEqualTo(250L);
            assertThat(status.getViewerCount()).isEqualTo(5);
            assertThat(status.getSecondsUntilDraw()).isEqualTo(30L);
        }

        @Test
        @DisplayName("con estado en cache: usa phase/revealedWinners/totalWinners del estado y propaga el parámetro totalParticipants")
        void withState_propagatesTotalParticipantsParameter() {
            cache.onDrawingStarted(1L, 2);
            cache.onWinnerRevealed(1L, winner(1));

            DrawStatusResponseDTO status = cache.buildStatus(1L, 5, 30L, null, 250L);

            assertThat(status.getCurrentPhase()).isEqualTo(DrawEventType.WINNER_REVEALED);
            assertThat(status.getRevealedWinners()).hasSize(1);
            assertThat(status.getTotalWinners()).isEqualTo(2);
            assertThat(status.getTotalParticipants()).isEqualTo(250L);
        }

        @Test
        @DisplayName("el parámetro raffle no se usa dentro del método: pasar null no afecta el resultado")
        void raffleParameterIsUnused() {
            DrawStatusResponseDTO withNullRaffle = cache.buildStatus(1L, 0, null, null, 0L);
            DrawStatusResponseDTO withRaffle = cache.buildStatus(1L, 0, null, new Raffle(), 0L);

            assertThat(withNullRaffle.getCurrentPhase()).isEqualTo(withRaffle.getCurrentPhase());
            assertThat(withNullRaffle.getTotalParticipants()).isEqualTo(withRaffle.getTotalParticipants());
        }
    }

    @Nested
    @DisplayName("concurrencia")
    class Concurrency {

        @Test
        @DisplayName("N hilos revelando ganadores a la vez para la misma rifa: no lanza excepciones y no se pierde ninguna actualización (revealedWinners es CopyOnWriteArrayList)")
        void concurrentWinnerReveals_neverThrow_andNoLostUpdates() throws InterruptedException {
            int threadCount = 20;
            cache.onDrawingStarted(1L, threadCount);

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger unexpectedErrors = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                int position = i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        cache.onWinnerRevealed(1L, winner(position));
                    } catch (Exception ex) {
                        unexpectedErrors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(completed).isTrue();
            assertThat(unexpectedErrors.get()).isZero();

            DrawStatusResponseDTO status = cache.buildStatus(1L, 0, null, null, 0L);
            List<WinnerRevealPayloadDTO> revealed = status.getRevealedWinners();
            assertThat(revealed).hasSize(threadCount);
        }

        @Test
        @DisplayName("lecturas de buildStatus en paralelo mientras otro hilo revela ganadores: nunca lanza (ConcurrentModificationException / AIOOBE)")
        void concurrentReadsDuringReveal_neverThrow() throws InterruptedException {
            int writes = 200;
            int readers = 8;
            cache.onDrawingStarted(1L, writes);

            ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(readers + 1);
            AtomicInteger unexpectedErrors = new AtomicInteger(0);

            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < writes; i++) {
                        cache.onWinnerRevealed(1L, winner(i));
                    }
                } catch (Exception ex) {
                    unexpectedErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            for (int r = 0; r < readers; r++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < writes; i++) {
                            cache.buildStatus(1L, 0, null, null, 0L).getRevealedWinners().size();
                        }
                    } catch (Exception ex) {
                        unexpectedErrors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(completed).isTrue();
            assertThat(unexpectedErrors.get()).isZero();
            assertThat(cache.buildStatus(1L, 0, null, null, 0L).getRevealedWinners()).hasSize(writes);
        }
    }
}
