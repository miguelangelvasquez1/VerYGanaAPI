package com.verygana2.services.raffles;

import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jmx.access.InvalidInvocationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerProofResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.exceptions.rafflesExceptions.InvalidRaffleStatusException;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;
import com.verygana2.models.Notification;
import com.verygana2.models.enums.NotificationType;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.NotificationRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.DrawingService;
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
    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleService raffleService;
    private final RandomOrgService randomOrgService;
    private final PrizeRepository prizeRepository;
    private final NotificationRepository notificationRepository;
    private final RaffleWinnerRepository raffleWinnerRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper;

    @Override
    public DrawResultResponseDTO conductDraw(Long raffleId, Integer numberOfWinners) {

        log.info("=== STARTING RAFFLE DRAW === Raffle ID: {}, Winners: {}", raffleId, numberOfWinners);

        // ========== 1. VALIDAR RIFA ==========

        Raffle raffle = validateRaffleForDraw(raffleId);

        // ========== 2. VALIDAR PREMIOS ==========
        List<Prize> prizes = validatePrizes(raffle);

        // Si numberOfWinners no se especifica, usar cantidad de premios
        if (numberOfWinners == null) {
            numberOfWinners = prizes.stream()
                    .mapToInt(Prize::getQuantity)
                    .sum();
        }

        // ========== 3. OBTENER TICKETS ACTIVOS ==========

        List<RaffleTicket> tickets = getActiveTickets(raffle);

        // ========== 4. EJECUTAR SORTEO ==========

        List<RaffleTicket> winningTickets = executeDraw(raffle, tickets, numberOfWinners);

        // ========== 5. CREAR REGISTROS DE GANADORES ==========

        List<RaffleWinner> winners = createWinnerRecords(raffle, prizes, winningTickets);

        // ========== 6. GENERAR PROOF ==========

        String drawProof = generateDrawProof(raffle.getId(), winners);
        raffle.setDrawProof(drawProof);

        // ========== 7. ACTUALIZAR ESTADO DE LA RIFA ==========

        updateRaffleStatus(raffle);

        // ========== 8. EXPIRAR TICKETS NO GANADORES ==========

        expireNonWinningTickets(raffle.getId());

        log.info("=== DRAW COMPLETED === Raffle: {}, Winners: {}", raffleId, winners.size());

        // ========== 9. NOTIFICAR Y PUBLICAR (ASYNC) ==========

        // Estos métodos se ejecutan de forma asíncrona
        notifyWinners(raffleId);
        publishResults(raffleId);

        // ========== 10. Construir response ==========
        return DrawResultResponseDTO.builder()
                .raffleId(raffleId)
                .numberOfWinners(winners.size())
                .winners(winners.stream()
                        .map(w -> WinnerSummaryResponseDTO.builder()
                                .winnerId(w.getId())
                                .consumerId(w.getWinner().getId())
                                .consumerName(w.getWinner().getUserName())
                                .ticketNumber(w.getWinningTicket().getTicketNumber())
                                .prizeTitle(w.getPrize().getTitle())
                                .position(w.getPrize().getPosition())
                                .build())
                        .toList())
                .message("Draw completed successfully")
                .build();
    }

    private Raffle validateRaffleForDraw(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        Raffle raffle = raffleService.getRaffleById(raffleId);

        if (raffle.getRaffleStatus() != RaffleStatus.CLOSED) {
            throw new InvalidRaffleStatusException(String.format("Cannot draw raffle with status: %s. Must be CLOSED",
                    raffle.getRaffleStatus()));
        }

        // Validar que la fecha de sorteo ha llegado o pasado
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
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

    private List<RaffleTicket> executeDraw(Raffle raffle, List<RaffleTicket> tickets, int numberOfWinners) {

        raffle.setRaffleStatus(RaffleStatus.DRAWING);
        raffleRepository.save(raffle);

        List<RaffleTicket> winners;

        switch (raffle.getDrawMethod()) {
            case RANDOM_ORG -> {
                log.info("Using RANDOM_ORG (external draw method)");
                winners = randomExternalDraw(tickets, numberOfWinners);
            }

            case SYSTEM_RANDOM -> {
                log.info("Using SYSTEM_RANDOM (internal draw method)");
                winners = randomInternalDraw(tickets, numberOfWinners);
            }

            default -> throw new InvalidOperationException(
                    "Unsupported draw method: " + raffle.getDrawMethod());
        }
        return winners;
    }

    private List<RaffleWinner> createWinnerRecords(Raffle raffle, List<Prize> prizes,
            List<RaffleTicket> winningTickets) {
        List<RaffleWinner> winners = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));

        int ticketIndex = 0;

        for (Prize p : prizes) {
            int quantity = p.getQuantity();

            for (int i = 0; i < quantity && ticketIndex < winningTickets.size(); i++) {
                RaffleTicket winningTicket = winningTickets.get(ticketIndex++);
                RaffleWinner winner = new RaffleWinner();

                winner.setRaffle(raffle);
                winner.setPrize(p);
                winner.setWinner(winningTicket.getTicketOwner());
                winner.setWinningTicket(winningTicket);
                winner.setDrawnAt(now);

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

        winners.forEach(w -> w.setWinner(true));
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
            List<Integer> randomIndexes = randomOrgService.generateRandomIntegers(0, tickets.size() - 1,
                    (int) numberOfWinners);
            List<RaffleTicket> winners = new ArrayList<>(numberOfWinners);

            for (Integer index : randomIndexes) {
                winners.add(tickets.get(index));
            }

            winners.forEach(w -> w.setWinner(true));

            raffleTicketRepository.saveAll(winners);

            log.info("External draw completed successfully. Winners: {}",
                    winners.stream()
                            .map(RaffleTicket::getTicketNumber)
                            .collect(Collectors.joining(", ")));

            return winners;

        } catch (RandomOrgException e) {
            log.error("Random.org external draw failed", e);

            log.warn("Falling back to internal draw algorithm");
            return randomInternalDraw(tickets, numberOfWinners);
        }
    }

    @Override
    public String generateDrawProof(Long raffleId, List<RaffleWinner> winners) {

        log.info("Generating draw proof for raffle {}", raffleId);

        try {
            Raffle raffle = raffleService.getRaffleById(raffleId);

            // Construir objeto de evidencia
            DrawProofResponseDTO proof = DrawProofResponseDTO.builder()
                    .raffleId(raffleId)
                    .raffleTitle(raffle.getTitle())
                    .drawMethod(raffle.getDrawMethod().toString())
                    .drawDate(raffle.getDrawDate())
                    .executedAt(ZonedDateTime.now(ZoneId.of("America/Bogota")))
                    .totalParticipants(raffle.getTotalParticipants())
                    .totalTickets(raffle.getTotalTicketsIssued())
                    .numberOfWinners(winners.size())
                    .winners(winners.stream()
                            .map(w -> WinnerProofResponseDTO.builder()
                                    .position(w.getPrize().getPosition())
                                    .ticketNumber(w.getWinningTicket().getTicketNumber())
                                    .consumerId(w.getWinner().getId())
                                    .prizeTitle(w.getPrize().getTitle())
                                    .drawnAt(w.getDrawnAt())
                                    .build())
                            .toList())
                    .build();

            // Convertir a JSON
            String proofJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(proof);

            log.info("Draw proof generated successfully");
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
    private void expireNonWinningTickets(Long raffleId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        int expiredCount = raffleTicketRepository.expireTicketsByRaffle(raffleId, now);

        log.info("Expired {} non-winning tickets for raffle {}", expiredCount, raffleId);
    }

    @Override
    public boolean verifyDrawIntegrity(Long raffleId) {

        log.info("Verifying draw integrity for raffle {}", raffleId);

        Raffle raffle = raffleService.getRaffleById(raffleId);

        // Verificar que tiene proof
        if (raffle.getDrawProof() == null || raffle.getDrawProof().isBlank()) {
            log.warn("Raffle {} has no draw proof", raffleId);
            return false;
        }

        // Verificar que está en estado COMPLETED
        if (raffle.getRaffleStatus() != RaffleStatus.COMPLETED) {
            log.warn("Raffle {} is not in COMPLETED status", raffleId);
            return false;
        }

        // Verificar que tiene ganadores
        long winnerCount = raffleWinnerRepository.countByRaffleId(raffleId);
        if (winnerCount == 0) {
            log.warn("Raffle {} has no winners", raffleId);
            return false;
        }

        // Verificar integridad del proof JSON
        try {
            objectMapper.readTree(raffle.getDrawProof());
            log.info("Draw integrity verification passed for raffle {}", raffleId);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Invalid draw proof JSON for raffle {}", raffleId, e);
            return false;
        }
    }

    @Override
    public void notifyWinners(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        log.info("Winner notifications triggered for raffle {}", raffleId);

        List<ConsumerDetails> winners = raffleWinnerRepository.findByRaffleId(raffleId).stream().map(t -> t.getWinner())
                .toList();
        List<Notification> notifications = new ArrayList<>(winners.size());

        winners.forEach(w -> {
            notifications.add(Notification.builder()
                    .type(NotificationType.IN_APP_NOTIFICATION)
                    .title("¡Felicidades! Has ganado en nuestra rifa 🎉")
                    .message("Estimado/a " + w.getUserName()
                            + ", nos complace anunciarte que has sido el ganador del premio en nuestra rifa oficial. Tu número de participación ha sido seleccionado de manera transparente y justa, y ahora podrás disfrutar de este reconocimiento especial.")
                    .user(w)
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

        Raffle raffle = raffleService.getRaffleById(raffleId);

        List<RaffleWinner> winners = raffleWinnerRepository.findByRaffleId(raffleId);

        log.info("Published results: Raffle '{}' - {} winners",
                raffle.getTitle(),
                winners.size());

        // notificationService.notifyAllParticipants(raffleId);
    }

}
