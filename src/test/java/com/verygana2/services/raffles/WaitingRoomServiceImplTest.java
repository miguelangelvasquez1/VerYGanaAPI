// package com.verygana2.services.raffles;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyLong;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.never;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Nested;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;

// import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
// import com.verygana2.services.interfaces.raffles.RaffleService;

// @ExtendWith(MockitoExtension.class)
// @DisplayName("WaitingRoomServiceImpl")
// class WaitingRoomServiceImplTest {

//     @Mock RaffleEventPublisherService raffleEventPublisherService;
//     @Mock RaffleService raffleService;

//     @InjectMocks WaitingRoomServiceImpl service;

//     // ─── addViewer ────────────────────────────────────────────────────────────

//     @Nested
//     @DisplayName("addViewer")
//     class AddViewer {

//         @Test
//         @DisplayName("adds session to raffle room and records session-to-raffle mapping")
//         void addsViewerSuccessfully() {
//             service.addViewer(1L, "session-A");

//             assertThat(service.getViewerCount(1L)).isEqualTo(1);
//         }

//         @Test
//         @DisplayName("multiple sessions in same room are all tracked")
//         void tracksMultipleSessionsPerRoom() {
//             service.addViewer(1L, "session-A");
//             service.addViewer(1L, "session-B");

//             assertThat(service.getViewerCount(1L)).isEqualTo(2);
//         }

//         @Test
//         @DisplayName("same session added twice is counted only once (Set semantics)")
//         void deduplicatesSameSession() {
//             service.addViewer(1L, "session-A");
//             service.addViewer(1L, "session-A");

//             assertThat(service.getViewerCount(1L)).isEqualTo(1);
//         }

//         @Test
//         @DisplayName("sessions in different rooms are counted independently")
//         void countsRoomsIndependently() {
//             service.addViewer(1L, "session-A");
//             service.addViewer(2L, "session-B");

//             assertThat(service.getViewerCount(1L)).isEqualTo(1);
//             assertThat(service.getViewerCount(2L)).isEqualTo(1);
//         }
//     }

//     // ─── removeViewer ─────────────────────────────────────────────────────────

//     @Nested
//     @DisplayName("removeViewer")
//     class RemoveViewer {

//         @Test
//         @DisplayName("decrements count after removal")
//         void removesViewerFromRoom() {
//             service.addViewer(1L, "session-A");
//             service.addViewer(1L, "session-B");

//             service.removeViewer(1L, "session-A");

//             assertThat(service.getViewerCount(1L)).isEqualTo(1);
//         }

//         @Test
//         @DisplayName("removes room entry when last session leaves")
//         void removesRoomWhenEmpty() {
//             service.addViewer(1L, "session-A");
//             service.removeViewer(1L, "session-A");

//             assertThat(service.getViewerCount(1L)).isEqualTo(0);
//         }

//         @Test
//         @DisplayName("does nothing when removing from non-existent room")
//         void gracefulOnMissingRoom() {
//             service.removeViewer(99L, "session-X");

//             assertThat(service.getViewerCount(99L)).isEqualTo(0);
//         }
//     }

//     // ─── removeViewerFromAllRooms ─────────────────────────────────────────────

//     @Nested
//     @DisplayName("removeViewerFromAllRooms")
//     class RemoveViewerFromAllRooms {

//         @Test
//         @DisplayName("removes session using stored raffle mapping")
//         void removesSessionByMapping() {
//             service.addViewer(1L, "session-A");
//             service.removeViewerFromAllRooms("session-A");

//             assertThat(service.getViewerCount(1L)).isEqualTo(0);
//         }

//         @Test
//         @DisplayName("does nothing for unknown session")
//         void gracefulOnUnknownSession() {
//             service.removeViewerFromAllRooms("unknown-session");

//             assertThat(service.getViewerCount(1L)).isEqualTo(0);
//         }
//     }

//     // ─── getViewerCount ───────────────────────────────────────────────────────

//     @Nested
//     @DisplayName("getViewerCount")
//     class GetViewerCount {

//         @Test
//         @DisplayName("returns 0 for raffle with no viewers")
//         void returnsZeroForUnknownRaffle() {
//             assertThat(service.getViewerCount(42L)).isEqualTo(0);
//         }

//         @Test
//         @DisplayName("returns correct count after additions")
//         void returnsCorrectCount() {
//             service.addViewer(5L, "s1");
//             service.addViewer(5L, "s2");
//             service.addViewer(5L, "s3");

//             assertThat(service.getViewerCount(5L)).isEqualTo(3);
//         }
//     }

//     // ─── clearRoom ───────────────────────────────────────────────────────────

//     @Nested
//     @DisplayName("clearRoom")
//     class ClearRoom {

//         @Test
//         @DisplayName("removes all sessions and resets count to 0")
//         void clearsAllSessionsInRoom() {
//             service.addViewer(1L, "s1");
//             service.addViewer(1L, "s2");

//             service.clearRoom(1L);

//             assertThat(service.getViewerCount(1L)).isEqualTo(0);
//         }

//         @Test
//         @DisplayName("cleared sessions are no longer tracked by session-to-raffle map")
//         void clearsSessionMappings() {
//             service.addViewer(1L, "s1");
//             service.clearRoom(1L);

//             // After clearing, removing by session should be a no-op
//             service.removeViewerFromAllRooms("s1");
//             assertThat(service.getViewerCount(1L)).isEqualTo(0);
//         }

//         @Test
//         @DisplayName("does nothing for room that does not exist")
//         void gracefulOnMissingRoom() {
//             service.clearRoom(99L);

//             assertThat(service.getViewerCount(99L)).isEqualTo(0);
//         }
//     }

//     // ─── broadcastWaitingRoomUpdates ──────────────────────────────────────────

//     @Nested
//     @DisplayName("broadcastWaitingRoomUpdates")
//     class BroadcastWaitingRoomUpdates {

//         @Test
//         @DisplayName("publishes update for rooms with active viewers and future draw date")
//         void publishesForRoomWithViewers() {
//             com.verygana2.models.raffles.Raffle raffle = new com.verygana2.models.raffles.Raffle();
//             raffle.setDrawDate(java.time.ZonedDateTime.now().plusHours(1));
//             raffle.setTotalTicketsIssued(50L);

//             service.addViewer(1L, "session-A");
//             when(raffleService.getRaffleById(1L)).thenReturn(raffle);

//             service.broadcastWaitingRoomUpdates();

//             verify(raffleEventPublisherService).publishWaitingRoomUpdate(eq(1L), eq(1), anyLong(), eq(50L));
//         }

//         @Test
//         @DisplayName("does not broadcast for empty rooms")
//         void skipsEmptyRooms() {
//             service.broadcastWaitingRoomUpdates();

//             verify(raffleEventPublisherService, never()).publishWaitingRoomUpdate(any(), any(), anyLong(), anyLong());
//         }

//         @Test
//         @DisplayName("does not broadcast when draw date has already passed")
//         void skipsWhenDrawDatePast() {
//             com.verygana2.models.raffles.Raffle raffle = new com.verygana2.models.raffles.Raffle();
//             raffle.setDrawDate(java.time.ZonedDateTime.now().minusHours(1));
//             raffle.setTotalTicketsIssued(10L);

//             service.addViewer(1L, "session-A");
//             when(raffleService.getRaffleById(1L)).thenReturn(raffle);

//             service.broadcastWaitingRoomUpdates();

//             verify(raffleEventPublisherService, never()).publishWaitingRoomUpdate(any(), any(), anyLong(), anyLong());
//         }

//         @Test
//         @DisplayName("continues broadcasting other rooms if one raises an exception")
//         void continuesOnError() {
//             com.verygana2.models.raffles.Raffle raffle = new com.verygana2.models.raffles.Raffle();
//             raffle.setDrawDate(java.time.ZonedDateTime.now().plusHours(1));
//             raffle.setTotalTicketsIssued(5L);

//             service.addViewer(1L, "session-A");
//             service.addViewer(2L, "session-B");

//             when(raffleService.getRaffleById(1L)).thenThrow(new RuntimeException("DB error"));
//             when(raffleService.getRaffleById(2L)).thenReturn(raffle);

//             service.broadcastWaitingRoomUpdates();

//             verify(raffleEventPublisherService).publishWaitingRoomUpdate(eq(2L), eq(1), anyLong(), eq(5L));
//         }
//     }
// }
