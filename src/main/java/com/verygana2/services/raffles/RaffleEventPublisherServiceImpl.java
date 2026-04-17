package com.verygana2.services.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.raffle.websocket.DrawCompletedPayloadDTO;
import com.verygana2.dtos.raffle.websocket.RaffleDrawEventDTO;
import com.verygana2.dtos.raffle.websocket.WaitingRoomPayloadDTO;
import com.verygana2.dtos.raffle.websocket.WinnerRevealPayloadDTO;
import com.verygana2.models.enums.raffles.DrawEventType;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaffleEventPublisherServiceImpl implements RaffleEventPublisherService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RaffleDrawStateCache drawStateCache;

    private static final String domain = "https://cdn.verygana.com/";

    private static final String RAFFLE_TOPIC = "/topic/raffle/";
    private static final int REVEAL_DELAY_MS = 15000; // 15 segundos entre ganadores

    @Override
    public void publishDrawingStarted(Long raffleId, long totalTickets, int totalWinners) {
        RaffleDrawEventDTO event = RaffleDrawEventDTO.builder()
                .type(DrawEventType.DRAWING_STARTED)
                .raffleId(raffleId)
                .timestamp(now())
                .payload(Map.of("totalTickets", totalTickets,
                        "totalWinners", totalWinners

                )) // El frontend sabe cuántas boletas animar
                .build();

        broadcast(raffleId, event);
        log.info("[WS] DRAWING_STARTED publicado para rifa {}", raffleId);
        drawStateCache.onDrawingStarted(raffleId, totalWinners);
    }

    @Override
    @Async("drawRevealExecutor")
    public void publishWinnersWithDelay(Long raffleId, List<RaffleWinner> winners, String raffleTitle) {
        int total = winners.size();

        for (int i = 0; i < total; i++) {
            RaffleWinner w = winners.get(i);

            sleep(REVEAL_DELAY_MS);

            WinnerRevealPayloadDTO payload = WinnerRevealPayloadDTO.builder()
                    .position(w.getPrize().getPosition())
                    .ticketNumber(w.getWinningTicket().getTicketNumber())
                    .userName(w.getWinner().getUserName())
                    .userAvatarUrl(w.getWinner().getAvatar().getImageUrl())
                    .prizeTitle(w.getPrize().getTitle())
                    .prizeImageUrl(domain + w.getPrize().getImageAsset().getObjectKey())
                    .prizeValue(w.getPrize().getValue())
                    .prizeType(w.getPrize().getPrizeType())
                    .revealOrder(i + 1)
                    .totalWinners(total)
                    .build();

            RaffleDrawEventDTO event = RaffleDrawEventDTO.builder()
                    .type(DrawEventType.WINNER_REVEALED)
                    .raffleId(raffleId)
                    .timestamp(now())
                    .payload(payload)
                    .build();

            broadcast(raffleId, event);
            drawStateCache.onWinnerRevealed(raffleId, payload);

            log.info("[WS] WINNER_REVEALED {}/{} para rifa {}: {}",
                    i + 1, total, raffleId, w.getWinner().getUserName());

        }

        publishDrawCompleted(raffleId, winners, raffleTitle, total);

    }

    @Override
    public void publishDrawCompleted(Long raffleId, List<RaffleWinner> winners, String raffleTitle,
            int totalParticipants) {
        List<WinnerRevealPayloadDTO> allWinners = winners.stream()
                .map(w -> WinnerRevealPayloadDTO.builder()
                        .position(w.getPrize().getPosition())
                        .ticketNumber(w.getWinningTicket().getTicketNumber())
                        .userName(w.getWinner().getUserName())
                        .prizeTitle(w.getPrize().getTitle())
                        .prizeValue(w.getPrize().getValue())
                        .prizeType(w.getPrize().getPrizeType())
                        .build())
                .toList();

        DrawCompletedPayloadDTO payload = DrawCompletedPayloadDTO.builder()
                .raffleTitle(raffleTitle)
                .allWinners(allWinners)
                .drawProofUrl("/api/raffles/" + raffleId + "/draw-proof")
                .totalParticipants(totalParticipants)
                .build();

        RaffleDrawEventDTO event = RaffleDrawEventDTO.builder()
                .type(DrawEventType.DRAW_COMPLETED)
                .raffleId(raffleId)
                .timestamp(now())
                .payload(payload)
                .build();

        broadcast(raffleId, event);
        drawStateCache.onDrawCompleted(raffleId);

        log.info("[WS] DRAW_COMPLETED publicado para rifa {}", raffleId);
    }

    @Override
    public void publishWaitingRoomUpdate(Long raffleId, int viewerCount, long secondsUntilDraw, long totalTickets) {
        WaitingRoomPayloadDTO payload = WaitingRoomPayloadDTO.builder()
                .viewerCount(viewerCount)
                .secondsUntilDraw(secondsUntilDraw)
                .totalTickets(totalTickets)
                .build();

       
        @SuppressWarnings("unused")
        RaffleDrawEventDTO event = RaffleDrawEventDTO.builder()
                .type(DrawEventType.WAITING_ROOM_UPDATE)
                .raffleId(raffleId)
                .timestamp(now())
                .payload(payload)
                .build();
    }

    // ============================================================
    // MÉTODOS PRIVADOS
    // ============================================================

    private void broadcast(Long raffleId, RaffleDrawEventDTO event) {
        messagingTemplate.convertAndSend(RAFFLE_TOPIC + raffleId, event);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[WS] Sleep interrumpido durante reveal de ganadores");
        }
    }

}
