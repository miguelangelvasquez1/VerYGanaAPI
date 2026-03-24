package com.verygana2.services.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.WaitingRoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitingRoomServiceImpl implements WaitingRoomService {

    private final RaffleEventPublisherService raffleEventPublisherService;
    private final RaffleService raffleService;

    // raffleId -> Set de sessionIds conectados
    private final Map<Long, Set<String>> viewers = new ConcurrentHashMap<>();

    // sessionId -> raffleId (mapa inverso para desconexiones automáticas)
    private final Map<String, Long> sessionToRaffle = new ConcurrentHashMap<>();


    @Override
    public void addViewer(Long raffleId, String sessionId) {
        viewers.computeIfAbsent(raffleId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionToRaffle.put(sessionId, raffleId);
        log.debug("[WaitingRoom] Viewer {} joined to raffle {}. Total: {}", sessionId, raffleId,
                getViewerCount(raffleId));
    }

    @Override
    public void removeViewer(Long raffleId, String sessionId) {
        Set<String> sessions = viewers.get(raffleId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                viewers.remove(raffleId);
            }
        }
        sessionToRaffle.remove(sessionId);
    }

    @Override
    public void removeViewerFromAllRooms(String sessionId) {
        Long raffleId = sessionToRaffle.get(sessionId);
        if (raffleId != null) {
            removeViewer(raffleId, sessionId);
            log.debug("[WaitingRoom] Session {} removed of raffle {} due to disconnection", sessionId, raffleId);
        }
    }

    @Override
    public int getViewerCount(Long raffleId) {
        Set<String> sessions = viewers.get(raffleId);
        return sessions != null ? sessions.size() : 0;
    }

    @Override
    public void clearRoom(Long raffleId) {
        Set<String> sessions = viewers.get(raffleId);
        if (sessions != null) {
            sessions.forEach(sessionToRaffle::remove);
            viewers.remove(raffleId);
        }
        log.info("[WaitingRoom] raffle {} room closed", raffleId);
    }

    @Override
    @Scheduled(fixedDelay = 10000)
    public void broadcastWaitingRoomUpdates() {
        viewers.forEach((raffleId, sessions) -> {
            if (sessions.isEmpty())
                return;

            try {
                var raffle = raffleService.getRaffleById(raffleId);
                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                long seconds = ChronoUnit.SECONDS.between(now, raffle.getDrawDate());

                if (seconds < 0)
                    return; // El sorteo ya empezó, dejar de emitir

                raffleEventPublisherService.publishWaitingRoomUpdate(
                        raffleId,
                        sessions.size(),
                        seconds,
                        raffle.getTotalTicketsIssued());
            } catch (Exception e) {
                log.error("[WaitingRoom] issuing update to raffle {} error", raffleId, e);
            }
        });
    }

    

}
