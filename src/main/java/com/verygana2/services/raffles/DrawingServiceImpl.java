package com.verygana2.services.raffles;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jmx.access.InvalidInvocationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RandomOrgDrawMetadata;
import com.verygana2.dtos.raffle.responses.RandomOrgDrawResult;
import com.verygana2.dtos.raffle.responses.WinnerProofResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.exceptions.rafflesExceptions.InvalidRaffleStatusException;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;
import com.verygana2.models.Notification;
import com.verygana2.models.enums.NotificationType;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.NotificationRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleResultRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.DrawingService;
import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
import com.verygana2.services.interfaces.raffles.RaffleResultService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RandomOrgService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DrawingServiceImpl implements DrawingService {
    private final RaffleRepository raffleRepository;
    private final RaffleResultRepository raffleResultRepository;
    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleService raffleService;
    private final RaffleEventPublisherService raffleEventPublisherService;
    private final RaffleResultService raffleResultService;
    private final RandomOrgService randomOrgService;
    private final PrizeRepository prizeRepository;
    private final NotificationRepository notificationRepository;
    private final RaffleWinnerRepository raffleWinnerRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper;

    @Override
    public DrawResultResponseDTO conductDraw(Long raffleId) {

        log.info("=== STARTING RAFFLE DRAW === Raffle ID: {}", raffleId);

        // ========== 1. VALIDAR RIFA ==========

        Raffle raffle = validateRaffleForDraw(raffleId);

        log.info("Validating prizes...");
        // ========== 2. VALIDAR PREMIOS ==========
        List<Prize> prizes = validatePrizes(raffle);

        Integer numberOfWinners = prizes.size();

        // ========== 3. OBTENER TICKETS ACTIVOS ==========
        List<RaffleTicket> tickets = getActiveTickets(raffle);

        // ========== 4. EJECUTAR SORTEO — agregar evento ==========
        raffle.setRaffleStatus(RaffleStatus.DRAWING);
        raffleRepository.save(raffle);
        raffleEventPublisherService.publishDrawingStarted(raffleId, numberOfWinners, raffle.getTotalTicketsIssued(), raffle.getMaxTotalTickets());

        log.info("Executing draw...");
        DrawExecution drawExecution = executeDraw(raffle, tickets, numberOfWinners);

        log.info("Creating raffle result...");
        RaffleResult result = createRaffleResult(raffle);

        // ========== 5. CREAR REGISTROS DE GANADORES ==========
        log.info("Creating winner records...");
        List<RaffleWinner> winners = createWinnerRecords(result, prizes, drawExecution.winners());

        // ========== 6. GENERAR PROOF ==========
        log.info("Generating draw proof...");
        String drawProof = generateDrawProof(raffle.getId(), winners,
                drawExecution.actualMethod(), drawExecution.methodNote(),
                drawExecution.randomOrgMetadata());
        result.setDrawProof(drawProof);

        // ========== 7. ACTUALIZAR ESTADO DE LA RIFA ==========
        log.info("Updating raffle status...");
        updateRaffleStatus(raffle);
        log.debug("Raffle status updated : {}", raffle.getRaffleStatus());

        // ========== 8. EXPIRAR TICKETS NO GANADORES ==========
        log.info("Expiring no winning tickets...");
        expireTickets(raffle.getId());

        log.info("=== DRAW COMPLETED === Raffle: {}, Winners: {}", raffleId, winners.size());

        // ========== 9. REVELAR, NOTIFICAR GANADORES Y TERMINAR SORTEO (ASYNC)
        // ==========
        List<RaffleWinner> initializedWinners = initializeWinnersForAsync(winners);
        raffleEventPublisherService.publishWinnersWithDelay(raffleId, initializedWinners, raffle.getTitle());

        // Notificaciones in-app también async
        notifyWinners(raffleId);
        publishResults(raffleId);

        // ========== 10. Construir response ==========
        return DrawResultResponseDTO.builder()
                .raffleId(raffleId)
                .numberOfWinners(winners.size())
                .winners(winners.stream()
                        .map(w -> WinnerSummaryResponseDTO.builder()
                                .userName(w.getWinner().getUserName())
                                .raffleTitle(raffle.getTitle())
                                .prizeValue(w.getPrize().getValue())
                                .prizeTitle(w.getPrize().getTitle())
                                .position(w.getPrize().getPosition())
                                .build())
                        .toList())
                .message("Draw Initiated successfully, winners will be revealed live.")
                .build();
    }

    private Raffle validateRaffleForDraw(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        Raffle raffle = raffleService.getRaffleById(raffleId);

        if (raffle.getRaffleStatus() != RaffleStatus.LIVE) {
            throw new InvalidRaffleStatusException(String.format("Cannot draw raffle with status: %s. Must be LIVE",
                    raffle.getRaffleStatus()));
        }

        // Validar que la fecha de sorteo ha llegado o pasado
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(raffle.getDrawDate())) {
            throw new InvalidOperationException(
                    String.format("Cannot draw raffle before draw date. Draw date: %s, Now: %s",
                            raffle.getDrawDate(), now));
        }

        // Validar que tiene método de sorteo configurado
        if (raffle.getDrawMethod() == null) {
            throw new InvalidOperationException("Raffle does not have a draw method configured");
        }

        log.info("Raffle validation passed: ID={}, Status={}, DrawMethod={}",
                raffleId, raffle.getRaffleStatus(), raffle.getDrawMethod());

        return raffle;
    }

    private List<Prize> validatePrizes(Raffle raffle) {
        List<Prize> prizes = prizeRepository.findByRaffleIdOrderByPositionAsc(raffle.getId());

        if (prizes.isEmpty()) {
            throw new InvalidOperationException(
                    "Cannot draw raffle without prizes. Raffle ID: " + raffle.getId());
        }

        log.info("Found {} prizes for raffle {}", prizes.size(), raffle.getId());
        return prizes;
    }

    private List<RaffleTicket> getActiveTickets(Raffle raffle) {
        List<RaffleTicket> activeTickets = raffleTicketRepository.findByRaffleIdAndStatus(raffle.getId(),
                RaffleTicketStatus.ACTIVE);
        if (activeTickets.isEmpty()) {
            throw new InvalidInvocationException(
                    "Cannot draw raffle without active tickets. Raffle ID: " + raffle.getId());
        }

        log.info("Found {} active tickets for raffle {}", activeTickets.size(), raffle.getId());
        return activeTickets;
    }

    private record DrawExecution(List<RaffleTicket> winners, DrawMethod actualMethod,
                                 String methodNote, RandomOrgDrawMetadata randomOrgMetadata) {}

    private record ExternalDrawResult(List<RaffleTicket> winners, RandomOrgDrawMetadata metadata) {}

    private DrawExecution executeDraw(Raffle raffle, List<RaffleTicket> tickets, int numberOfWinners) {

        switch (raffle.getDrawMethod()) {
            case RANDOM_ORG -> {
                log.info("Using RANDOM_ORG (external draw method)");
                try {
                    ExternalDrawResult ext = executeExternalDrawWithMetadata(tickets, numberOfWinners);
                    return new DrawExecution(ext.winners(), DrawMethod.RANDOM_ORG, null, ext.metadata());
                } catch (Exception e) {
                    log.warn("Random.org failed ({}). Falling back to SYSTEM_RANDOM.", e.getMessage());
                    List<RaffleTicket> winners = randomInternalDraw(tickets, numberOfWinners);
                    return new DrawExecution(winners, DrawMethod.SYSTEM_RANDOM,
                            "Fallback to SYSTEM_RANDOM: Random.org unavailable — " + e.getMessage(), null);
                }
            }
            case SYSTEM_RANDOM -> {
                log.info("Using SYSTEM_RANDOM (internal draw method)");
                List<RaffleTicket> winners = randomInternalDraw(tickets, numberOfWinners);
                return new DrawExecution(winners, DrawMethod.SYSTEM_RANDOM, null, null);
            }
            default -> throw new InvalidOperationException(
                    "Unsupported draw method: " + raffle.getDrawMethod());
        }
    }

    private ExternalDrawResult executeExternalDrawWithMetadata(List<RaffleTicket> tickets, int numberOfWinners) {
        RandomOrgDrawResult result = randomOrgService.generateRandomIntegers(0, tickets.size() - 1, numberOfWinners);
        List<RaffleTicket> winners = new ArrayList<>(numberOfWinners);
        for (Integer index : result.indices()) {
            winners.add(tickets.get(index));
        }
        winners.forEach(w -> w.setIsWinner(true));
        raffleTicketRepository.saveAll(winners);
        log.info("External draw completed. Serial: {}. Winners: {}",
                result.metadata().getSerialNumber(),
                winners.stream().map(RaffleTicket::getTicketNumber).collect(Collectors.joining(", ")));
        return new ExternalDrawResult(winners, result.metadata());
    }

    private RaffleResult createRaffleResult(Raffle raffle) {
        RaffleResult result = new RaffleResult();
        result.setRaffle(raffle);
        return raffleResultRepository.save(result);
    }

    private List<RaffleWinner> createWinnerRecords(RaffleResult result, List<Prize> prizes,
            List<RaffleTicket> winningTickets) {
        List<RaffleWinner> winners = new ArrayList<>();

        int ticketIndex = 0;

        for (Prize p : prizes) {
            int quantity = p.getQuantity();

            for (int i = 0; i < quantity && ticketIndex < winningTickets.size(); i++) {
                RaffleTicket winningTicket = winningTickets.get(ticketIndex++);
                RaffleWinner winner = new RaffleWinner();

                winner.setRaffleResult(result);
                winner.setPrize(p);
                winner.setWinner(winningTicket.getTicketOwner());
                winner.setWinningTicket(winningTicket);

                winners.add(winner);

                log.info("Winner created: Ticket={}, Consumer={}, Prize={}",
                        winningTicket.getTicketNumber(),
                        winningTicket.getTicketOwner().getId(),
                        p.getTitle());
            }
        }

        return raffleWinnerRepository.saveAll(winners);
    }

    @Override
    public List<RaffleTicket> randomInternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners) {

        if (tickets == null || tickets.isEmpty()) {
            throw new InvalidOperationException("Cannot draw from empty ticket list");
        }

        if (numberOfWinners == null || numberOfWinners <= 0) {
            throw new InvalidOperationException("Number of winners must be positive");
        }

        if (numberOfWinners > tickets.size()) {
            throw new InvalidOperationException(
                    String.format("Cannot select %d winners from %d tickets",
                            numberOfWinners, tickets.size()));
        }

        log.info("Internal draw: selecting {} winners from {} tickets",
                numberOfWinners, tickets.size());

        List<RaffleTicket> shuffledTickets = new ArrayList<>(tickets);
        Collections.shuffle(shuffledTickets, secureRandom);
        List<RaffleTicket> winners = shuffledTickets.subList(0, numberOfWinners);

        winners.forEach(w -> w.setIsWinner(true));
        raffleTicketRepository.saveAll(winners);

        log.info("Internal draw completed. Winners: {}",
                winners.stream()
                        .map(RaffleTicket::getTicketNumber)
                        .collect(Collectors.joining(", ")));

        return winners;
    }

    @Override
    public List<RaffleTicket> randomExternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners) {

        if (tickets == null || tickets.isEmpty()) {
            throw new InvalidOperationException("Cannot draw from empty ticket list");
        }

        if (numberOfWinners == null || numberOfWinners <= 0) {
            throw new InvalidOperationException("Number of winners must be positive");
        }

        if (numberOfWinners > tickets.size()) {
            throw new InvalidOperationException(
                    String.format("Cannot select %d winners from %d tickets",
                            numberOfWinners, tickets.size()));
        }

        log.info("Starting external draw with Random.org: {} winners from {} tickets",
                numberOfWinners, tickets.size());

        try {
            RandomOrgDrawResult result = randomOrgService.generateRandomIntegers(0, tickets.size() - 1,
                    (int) numberOfWinners);
            List<RaffleTicket> winners = new ArrayList<>(numberOfWinners);

            for (Integer index : result.indices()) {
                winners.add(tickets.get(index));
            }

            winners.forEach(w -> w.setIsWinner(true));
            raffleTicketRepository.saveAll(winners);

            log.info("External draw completed successfully. Serial: {}. Winners: {}",
                    result.metadata().getSerialNumber(),
                    winners.stream().map(RaffleTicket::getTicketNumber).collect(Collectors.joining(", ")));

            return winners;

        } catch (RandomOrgException e) {
            log.error("Random.org external draw failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public String generateDrawProof(Long raffleId, List<RaffleWinner> winners,
                                    DrawMethod actualMethod, String drawMethodNote,
                                    RandomOrgDrawMetadata randomOrgMetadata) {

        log.info("Generating draw proof for raffle {}", raffleId);

        try {
            Raffle raffle = raffleService.getRaffleById(raffleId);

            DrawProofResponseDTO proof = DrawProofResponseDTO.builder()
                    .raffleId(raffleId)
                    .raffleTitle(raffle.getTitle())
                    .configuredDrawMethod(raffle.getDrawMethod().toString())
                    .actualDrawMethod(actualMethod.toString())
                    .drawMethodNote(drawMethodNote)
                    .randomOrgMetadata(randomOrgMetadata)
                    .drawDate(raffle.getDrawDate())
                    .executedAt(ZonedDateTime.now(ZoneOffset.UTC))
                    .totalParticipants(raffle.getTotalParticipants())
                    .totalTickets(raffle.getTotalTicketsIssued())
                    .numberOfWinners(winners.size())
                    .winners(winners.stream()
                            .map(w -> WinnerProofResponseDTO.builder()
                                    .userName(w.getWinner().getUserName())
                                    .ticketNumber(w.getWinningTicket().getTicketNumber())
                                    .position(w.getPrize().getPosition())
                                    .prizeTitle(w.getPrize().getTitle())
                                    .prizeType(w.getPrize().getPrizeType())
                                    .prizeValue(w.getPrize().getValue())
                                    .prizeClaimed(w.isPrizeClaimed())
                                    .claimDeadline(w.getClaimDeadline())
                                    .prizeClaimedAt(w.getPrizeClaimedAt())
                                    .prizeTrackingNumber(w.getPrizeTrackingNumber())
                                    .build())
                            .toList())
                    .build();

            String proofJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(proof);

            log.info("Draw proof generated. configuredMethod={}, actualMethod={}, randomOrgSerial={}",
                    raffle.getDrawMethod(), actualMethod,
                    randomOrgMetadata != null ? randomOrgMetadata.getSerialNumber() : "N/A");
            return proofJson;

        } catch (JsonProcessingException e) {
            log.error("Failed to generate draw proof", e);
            throw new InvalidOperationException("Failed to generate draw proof: " + e.getMessage());
        }
    }

    /**
     * 7. Actualiza el estado de la rifa a COMPLETED
     */
    private void updateRaffleStatus(Raffle raffle) {
        raffle.setRaffleStatus(RaffleStatus.COMPLETED);
        raffleRepository.save(raffle);

        log.info("Raffle status updated to COMPLETED: {}", raffle.getId());
    }

    /**
     * 8. Expira todos los tickets que no ganaron
     */
    private void expireTickets(Long raffleId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int expiredCount = raffleTicketRepository.expireTicketsByRaffle(raffleId, now);

        log.info("Expired {} non-winning tickets for raffle {}", expiredCount, raffleId);
    }

    @Override
    public boolean verifyDrawIntegrity(Long raffleId) {

        log.info("Verifying draw integrity for raffle {}", raffleId);

        RaffleResult result = raffleResultService.getByRaffleId(raffleId);

        // Verificar que tiene proof
        if (result.getDrawProof() == null || result.getDrawProof().isBlank()) {
            log.warn("Raffle {} has no draw proof", raffleId);
            return false;
        }

        // Verificar que está en estado COMPLETED
        if (result.getRaffle().getRaffleStatus() != RaffleStatus.COMPLETED) {
            log.warn("Raffle {} is not in COMPLETED status", raffleId);
            return false;
        }

        // Verificar que tiene ganadores
        long winnerCount = raffleWinnerRepository.countByRaffleResultId(result.getId());
        if (winnerCount == 0) {
            log.warn("Raffle {} has no winners", raffleId);
            return false;
        }

        // Verificar integridad del proof JSON
        try {
            objectMapper.readTree(result.getDrawProof());
            log.info("Draw integrity verification passed for raffle {}", raffleId);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Invalid draw proof JSON for raffle {}", raffleId, e);
            return false;
        }
    }

    @Override
    @Async("notificationExecutor")
    public void notifyWinners(Long raffleResultId) {

        if (raffleResultId == null || raffleResultId <= 0) {
            throw new IllegalArgumentException("Raffle result id must be positive");
        }

        log.info("Winner notifications triggered for raffle result {}", raffleResultId);

        List<ConsumerDetails> winners = raffleWinnerRepository.findByRaffleResultId(raffleResultId).stream()
                .map(t -> t.getWinner())
                .toList();
        List<Notification> notifications = new ArrayList<>(winners.size());

        winners.forEach(w -> {
            notifications.add(Notification.builder()
                    .type(NotificationType.IN_APP_NOTIFICATION)
                    .title("¡Felicidades! Has ganado en nuestra rifa 🎉")
                    .message("Estimado/a " + w.getUserName()
                            + ", nos complace anunciarte que has sido el ganador del premio en nuestra rifa oficial. Tu número de participación ha sido seleccionado de manera transparente y justa, y ahora podrás disfrutar de este reconocimiento especial.")
                    .user(w)
                    .dateSent(Instant.now())
                    .build());
        });

        try {
            notificationRepository.saveAll(notifications);
            log.info("Notifications sent successfully to {} winners", winners.size());
        } catch (Exception e) {

            log.error("Failed to send notifications to winners", e);
        }
    }

    @Override
    public void publishResults(Long raffleId) {
        // TODO: Implementar publicación de resultados
        // - Actualizar página pública de resultados
        // - Enviar a redes sociales (si aplica)
        // - Notificar a todos los participantes

        log.info("Results published for raffle {}", raffleId);

        RaffleResult result = raffleResultService.getByRaffleId(raffleId);

        List<RaffleWinner> winners = raffleWinnerRepository.findByRaffleResultId(result.getId());

        log.info("Published results: Raffle '{}' - {} winners",
                result.getRaffle().getTitle(),
                winners.size());

        // notificationService.notifyAllParticipants(raffleId);
    }


    // Metodos privados

    /**
     * Fuerza la inicialización de todas las relaciones lazy que
     * publishWinnersWithDelay necesita fuera de la transacción.
     * Debe llamarse dentro de la transacción de conductDraw().
     */
    private List<RaffleWinner> initializeWinnersForAsync(List<RaffleWinner> winners) {
        winners.forEach(w -> {
            // Forzar carga de cada relación lazy accediendo a un campo
            w.getWinner().getUserName();
            w.getWinner().getAvatar().getImageUrl();
            w.getWinningTicket().getTicketNumber();
            w.getPrize().getTitle();
            w.getPrize().getValue();
            w.getPrize().getPrizeType();
            w.getPrize().getPosition();
        });
        return winners;
    }

}
