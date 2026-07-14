package com.verygana2.services.raffles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.mappers.raffles.PrizeMapper;
import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
import com.verygana2.services.interfaces.raffles.RaffleService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link WaitingRoomServiceImpl}: el conteo de espectadores en
 * memoria de la sala de espera de una rifa (join/leave/desconexión), sin
 * tocar los métodos programados (broadcastWaitingRoomUpdates no se testea
 * aquí porque orquesta el resto de servicios mockeados, ya cubiertos por
 * separado en sus propios tests).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingRoomServiceImpl")
class WaitingRoomServiceImplTest {

    @Mock private RaffleEventPublisherService raffleEventPublisherService;
    @Mock private RaffleService raffleService;
    @Mock private PrizeMapper prizeMapper;

    private WaitingRoomServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WaitingRoomServiceImpl(raffleEventPublisherService, raffleService, prizeMapper);
    }

    @Test
    @DisplayName("addViewer: suma un espectador a la sala de la rifa")
    void addViewer_incrementsCount() {
        service.addViewer(1L, "session-a");
        service.addViewer(1L, "session-b");

        assertThat(service.getViewerCount(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("addViewer con la misma sesión dos veces: no duplica (es un Set)")
    void addViewer_sameSessionTwice_doesNotDuplicate() {
        service.addViewer(1L, "session-a");
        service.addViewer(1L, "session-a");

        assertThat(service.getViewerCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("removeViewer: resta un espectador de la sala")
    void removeViewer_decrementsCount() {
        service.addViewer(1L, "session-a");
        service.addViewer(1L, "session-b");

        service.removeViewer(1L, "session-a");

        assertThat(service.getViewerCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("removeViewerFromAllRooms: quita la sesión de la sala en la que estaba, sin necesidad de saber el raffleId")
    void removeViewerFromAllRooms_removesFromCorrectRoom() {
        service.addViewer(1L, "session-a");
        service.addViewer(2L, "session-b");

        service.removeViewerFromAllRooms("session-a");

        assertThat(service.getViewerCount(1L)).isZero();
        assertThat(service.getViewerCount(2L)).isEqualTo(1);
    }

    @Test
    @DisplayName("getViewerCount: 0 para una rifa sin espectadores conectados")
    void getViewerCount_zeroForUnknownRaffle() {
        assertThat(service.getViewerCount(999L)).isZero();
    }
}
