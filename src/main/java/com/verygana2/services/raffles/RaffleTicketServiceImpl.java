package com.verygana2.services.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketBalanceResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.mappers.raffles.RaffleTicketMapper;
import com.verygana2.models.enums.raffles.AuditAction;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleParticipation;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.TicketAuditLog;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleRuleRespository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketAuditLogRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.raffles.RaffleRuleService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RaffleTicketServiceImpl implements RaffleTicketService {

    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleRepository raffleRepository;
    private final RaffleRuleRespository raffleRuleRespository;
    private final RaffleRuleService raffleRuleService;
    private final RaffleService raffleService;
    private final RaffleTicketMapper raffleTicketMapper;
    private final ConsumerDetailsService consumerDetailsService;
    private final RaffleParticipationRepository participationRepository;
    private final TicketAuditLogRepository auditLogRepository;

    // Emitir tiquetes de una rifa a un usuario
    @Override
    public List<RaffleTicketResponseDTO> issueTickets(Long consumerId, Long raffleId, Integer quantity,
            RaffleTicketSource source, Long sourceId) {

        log.info("🎫 ENTRY: issueTickets(consumerId={}, raffleId={}, quantity={}, source={}, sourceId={})",
                consumerId, raffleId, quantity, source, sourceId);

        validateSourceId(sourceId, source);

        return issueTicketsAux(consumerId, raffleId, quantity, source, sourceId);
    }

    /**
     * Método auxiliar que realiza la emisión de tickets
     */
    @SuppressWarnings("null")
    private List<RaffleTicketResponseDTO> issueTicketsAux(Long consumerId, Long raffleId, Integer quantity,
            RaffleTicketSource source, Long sourceId) {

        log.info("Starting ticket issuance process...");

        log.debug("Validating and fetching raffle...");
        Raffle raffle = validateAndGetRaffle(raffleId);
        log.info("Raffle validated: ID={}, Status={}, Type={}",
                raffle.getId(), raffle.getRaffleStatus(), raffle.getRaffleType());

        log.debug("Validating and fetching consumer...");
        ConsumerDetails consumer = validateAndGetConsumer(consumerId);
        log.info("Consumer validated: ID={}, HasPet={}",
                consumer.getId(), consumer.isHasPet());

        log.debug("Validating user eligibility...");
        validateUserEligibility(consumer, raffle.getRaffleType());
        log.info("User eligibility validated");

        log.debug("Validating limits...");
        validateLimits(raffle, consumer.getId(), quantity, source);
        log.info("Limits validated");

        log.debug("Generating {} tickets...", quantity);
        List<RaffleTicket> tickets = generateTickets(
                raffle,
                consumer,
                quantity,
                source,
                sourceId);
        log.info("Generated {} tickets", tickets.size());

        log.debug("Saving tickets to database...");
        List<RaffleTicket> savedTickets = raffleTicketRepository.saveAll(tickets);
        log.info("Saved {} tickets to database", savedTickets.size());

        log.debug("Updating counters...");
        updateAllCounters(raffle, consumer, quantity, source);
        log.info("Counters updated");

        log.debug("Creating audit logs...");
        createAuditLogs(savedTickets, source, sourceId);
        log.info("Audit logs created");

        log.info("Successfully issued {} tickets to consumer {} for raffle {} from source {}",
                quantity, consumer.getId(), raffle.getId(), source);

        return savedTickets.stream().map(raffleTicketMapper::toRaffleTicketResponseDTO).toList();
    }

    /**
     * Valida que sourceId esté presente para fuentes que lo requieren
     */
    private void validateSourceId(Long sourceId, RaffleTicketSource source) {

        if (sourceId == null || sourceId <= 0) {
            throw new InvalidRequestException(
                    "Source ID is required for source type: " + source);
        }
    }

    /**
     * Valida que la rifa exista y esté activa
     */
    private Raffle validateAndGetRaffle(Long raffleId) {

        Raffle raffle = raffleService.getRaffleById(raffleId);

        if (raffle.getRaffleStatus() != RaffleStatus.ACTIVE) {
            throw new InvalidRequestException(
                    "Cannot issue tickets for raffle with status: " + raffle.getRaffleStatus());
        }

        // Validar que no haya expirado
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isAfter(raffle.getEndDate())) {
            throw new InvalidRequestException("Raffle has already ended");
        }

        return raffle;
    }

    /**
     * Valida que el consumidor exista
     */
    private ConsumerDetails validateAndGetConsumer(Long consumerId) {
        return consumerDetailsService.getConsumerById(consumerId);
    }

    /**
     * Valida que el usuario pueda participar en el tipo de rifa
     */
    private void validateUserEligibility(ConsumerDetails consumer, RaffleType raffleType) {
        log.debug("Checking eligibility: RaffleType={}, UserHasPet={}",
                raffleType, consumer.isHasPet());

        if (raffleType == RaffleType.PREMIUM && !consumer.isHasPet()) {
            log.error("User {} cannot participate in PREMIUM raffle (no pet registered)",
                    consumer.getId());

            throw new InvalidRequestException(
                    "Premium raffles require the user to have a registered pet");
        }
        log.debug("User is eligible for {} raffle", raffleType);
    }

    /**
     * Valida todos los límites antes de emitir tickets
     */
    private void validateLimits(Raffle raffle, Long consumerId, int quantity, RaffleTicketSource source) {
        log.debug("Validating limits for {} tickets...", quantity);

        // 1. Límite total de la rifa
        log.debug("Total: {}/{}", raffle.getTotalTicketsIssued(), raffle.getMaxTotalTickets());

        if (raffle.hasReachedTotalLimit()) {
            log.error("Raffle has reached maximum total tickets");
            throw new LimitReachedException("Raffle has reached maximum total tickets");
        }

        if (raffle.getMaxTotalTickets() != null &&
                (raffle.getTotalTicketsIssued() + quantity) > raffle.getMaxTotalTickets()) {
            log.error("Cannot issue {} tickets. Only {} remaining",
                    quantity, raffle.getMaxTotalTickets() - raffle.getTotalTicketsIssued());
            throw new LimitReachedException(
                    String.format("Cannot issue %d tickets. Only %d tickets remaining",
                            quantity,
                            raffle.getMaxTotalTickets() - raffle.getTotalTicketsIssued()));
        }

        // 2. Límite por fuente
        log.debug("Checking source limit for {} ...", source);
        TicketEarningRuleType ruleType = convertSourceToRuleType(source);
        log.debug("Converted {} to {}", source, ruleType);
        RaffleRule rule = raffleRuleService.getByRaffleIdAndRuleType(raffle.getId(), ruleType);
        log.debug("Source: {}/{}", rule.getCurrentTicketsBySource(), rule.getMaxTicketsBySource());

        if (!rule.canIssueTickets(quantity)) {
            log.error("Maximum tickets from source {} reached. Remaining: {}", 
                source, rule.getRemainingTickets());
            throw new LimitReachedException(
                    String.format("Maximum tickets from source %s reached for this raffle. Remaining: %d",
                            source, rule.getRemainingTickets()));
        }

        // 3. Límite por usuario
        if (raffle.getMaxTicketsPerUser() != null) {
            long currentUserTickets = raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(
                    consumerId,
                    raffle.getId(),
                    RaffleTicketStatus.ACTIVE);

            log.debug("User: {}/{}", currentUserTickets, raffle.getMaxTicketsPerUser());

            if ((currentUserTickets + quantity) > raffle.getMaxTicketsPerUser()) {
                log.error("User has reached maximum tickets. Current: {}, Max: {}", 
                    currentUserTickets, raffle.getMaxTicketsPerUser());
                throw new LimitReachedException(
                        String.format("User has reached maximum tickets per user. Current: %d, Max: %d",
                                currentUserTickets,
                                raffle.getMaxTicketsPerUser()));
            }
        }
        log.debug("All limits validated successfully");
    }

    /**
     * Genera los tickets con números únicos
     */
    private List<RaffleTicket> generateTickets(
            Raffle raffle,
            ConsumerDetails consumer,
            int quantity,
            RaffleTicketSource source,
            Long sourceId) {
        List<RaffleTicket> tickets = new ArrayList<>(quantity);

        // Obtener el siguiente número secuencial
        long startSequence = raffle.getTotalTicketsIssued() + 1;

        for (int i = 0; i < quantity; i++) {
            RaffleTicket ticket = new RaffleTicket();
            ticket.setRaffle(raffle);
            ticket.setTicketOwner(consumer);
            ticket.setSource(source);
            ticket.setSourceId(sourceId);

            // ✅ GENERACIÓN DE NÚMERO ÚNICO SECUENCIAL
            String ticketNumber = generateUniqueTicketNumber(raffle.getId(), startSequence + i);
            ticket.setTicketNumber(ticketNumber);

            // El @PrePersist configura: status=ACTIVE, isWinner=false, issuedAt=now
            tickets.add(ticket);
        }

        return tickets;
    }

    /**
     * Genera un número de ticket único y secuencial
     * Formato: RAFFLE-{raffleId}-{sequentialNumber}
     * Ejemplo: RAFFLE-123-000001
     */
    private String generateUniqueTicketNumber(Long raffleId, long sequentialNumber) {
        return String.format("RAFFLE-%d-%06d", raffleId, sequentialNumber);
    }

    private void updateAllCounters(Raffle raffle, ConsumerDetails consumer, int quantity, RaffleTicketSource source) {

        // 1. Actualizar contador total de la rifa
        raffle.incrementTicketCount(quantity);
        raffleRepository.save(raffle);

        // 2. Actualizar contador de RaffleDetails (por fuente)
        TicketEarningRuleType ruleType = convertSourceToRuleType(source);
        RaffleRule rule = raffleRuleService.getByRaffleIdAndRuleType(raffle.getId(), ruleType);

        rule.incrementIssuedCount(quantity);
        raffleRuleRespository.save(rule);

        // 3. Actualizar o crear participación del usuario
        updateParticipation(consumer, raffle, quantity);
    }

    /**
     * Actualiza o crea la participación del usuario en la rifa
     */
    private void updateParticipation(ConsumerDetails consumer, Raffle raffle, int quantity) {
        RaffleParticipation participation = participationRepository
                .findByConsumerIdAndRaffleId(consumer.getId(), raffle.getId())
                .orElseGet(() -> {
                    RaffleParticipation newParticipation = new RaffleParticipation();
                    newParticipation.setConsumer(consumer);
                    newParticipation.setRaffle(raffle);
                    newParticipation.setTicketsCount(0L);
                    raffle.incrementParticipantCount();
                    return newParticipation;
                });

        // Incrementar contador de tickets
        participation.setTicketsCount(participation.getTicketsCount() + quantity);
        participation.setLastParticipationAt(ZonedDateTime.now());

        participationRepository.save(participation);
    }

    /**
     * Crea registros de auditoría para cada ticket emitido
     */
    @SuppressWarnings("null")
    private void createAuditLogs(
            List<RaffleTicket> tickets,
            RaffleTicketSource source,
            Long sourceId) {
        List<TicketAuditLog> logs = tickets.stream()
                .map(ticket -> {
                    TicketAuditLog log = new TicketAuditLog();
                    log.setTicket(ticket);
                    log.setAction(AuditAction.ISSUED);
                    log.setSourceType(source);
                    log.setSourceId(sourceId);
                    // Opcional: agregar IP si tienes acceso al HttpServletRequest
                    return log;
                })
                .toList();

        auditLogRepository.saveAll(logs);
    }

    @Override
    public boolean canUserReceiveTickets(Long consumerId, RaffleType raffleType) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        if (raffleType == RaffleType.STANDARD) {
            return true;
        }
        return consumerDetailsService.getConsumerById(consumerId).isHasPet();
    }

    @Override
    public Long getUserTicketBalanceInRaffle(Long consumerId, Long raffleId, RaffleTicketStatus status) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(consumerId, raffleId, status);
    }

    @Override
    public Long getUserTotalTickets(Long consumerId, RaffleTicketStatus status) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return raffleTicketRepository.countByTicketOwnerIdAndStatus(consumerId, status);
    }

    @Override
    public List<TicketBalanceResponseDTO> getUserTicketBalanceByRaffle(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        // Query que agrupe tickets por raffle
        List<Object[]> results = raffleTicketRepository
                .countTicketsByTicketOwnerGroupedByRaffle(consumerId);

        return results.stream()
                .map(row -> TicketBalanceResponseDTO.builder()
                        .raffleId((Long) row[0])
                        .raffleTitle((String) row[1])
                        .raffleType((RaffleType) row[2])
                        .ticketsCount((Long) row[3])
                        .drawDate((ZonedDateTime) row[4])
                        .raffleStatus((RaffleStatus) row[5])
                        .build())
                .toList();
    }

    @Override
    public PagedResponse<RaffleTicketResponseDTO> getUserTickets(Long consumerId, RaffleTicketStatus status,
            RaffleTicketSource source, ZonedDateTime issuedAt, Pageable pageable) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }
        Page<RaffleTicketResponseDTO> page = raffleTicketRepository
                .findUserTicketsWithFilters(consumerId, status, source, issuedAt, pageable)
                .map(raffleTicketMapper::toRaffleTicketResponseDTO);
        return PagedResponse.from(page);
    }

    @Override
    public PagedResponse<RaffleTicketResponseDTO> getTicketsByRaffle(Long raffleId, RaffleTicketStatus status,
            RaffleTicketSource source, ZonedDateTime issuedAt, Pageable pageable) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        Page<RaffleTicketResponseDTO> page = raffleTicketRepository
                .findRaffleTicketsWithFilters(raffleId, status, source, issuedAt, pageable)
                .map(raffleTicketMapper::toRaffleTicketResponseDTO);
        return PagedResponse.from(page);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateTicket(String ticketNumber) {
        return raffleTicketRepository.findByTicketNumber(ticketNumber)
                .map(ticket -> ticket.getStatus() == RaffleTicketStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public void expireTickets(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        int expiredCount = raffleTicketRepository.expireTicketsByRaffle(raffleId,
                ZonedDateTime.now(ZoneId.of("America/Bogota")));

        log.info("Expired {} tickets for raffle {}", expiredCount, raffleId);
    }

    // ==================== UTILIDADES ====================

    private TicketEarningRuleType convertSourceToRuleType(RaffleTicketSource source) {
        return switch (source) {
            case PURCHASE -> TicketEarningRuleType.PURCHASE;
            case GAME_ACHIEVEMENT -> TicketEarningRuleType.GAME_ACHIEVEMENT;
            case REFERRAL -> TicketEarningRuleType.REFERRAL;
            case ADS_WATCHED -> TicketEarningRuleType.ADS_WATCHED;
            default -> throw new InvalidRequestException("Unknown source type: " + source);
        };
    }

}
