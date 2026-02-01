package com.verygana2.services.raffles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.IssueTicketRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.RuleResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.mappers.raffles.TicketEarningRuleMapper;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;
import com.verygana2.services.interfaces.raffles.TicketEarningRuleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TicketEarningRuleServiceImpl implements TicketEarningRuleService {

    private final TicketEarningRuleRepository ruleRepository;
    private final TicketEarningRuleMapper ruleMapper;
    private final RaffleRepository raffleRepository;
    private final RaffleTicketRepository ticketRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final RaffleTicketService raffleTicketService;

    @Override
    public TicketEarningRule getTicketEarningRuleById(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new IllegalArgumentException("Ticket earning rule id must be positive");
        }

        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ObjectNotFoundException("Ticket rule with id: " + ruleId + " not found ",
                        TicketEarningRule.class));
    }

    // ========== EVENT LISTENERS ==========

    @Override
    public void onPurchaseCompleted(Purchase purchase) {

        log.info("Processing purchase completed event: PurchaseId={}, ConsumerId={}, Amount={}",
                purchase.getId(),
                purchase.getConsumer().getId(),
                purchase.getTotal());

        // Obtener reglas activas de tipo PURCHASE
        List<TicketEarningRule> rules = getActiveRulesByType(RaffleTicketSource.PURCHASE);

        if (rules.isEmpty()) {
            log.info("No active PURCHASE rules found");
            return;
        }

        // Evaluar cada regla
        for (TicketEarningRule rule : rules) {
            try {
                if (evaluateRule(rule, purchase.getConsumer().getId(), RaffleTicketSource.PURCHASE, purchase)) {

                    // Calcular tickets según la regla
                    Integer tickets = calculateTicketsForRule(rule, purchase);

                    if (tickets > 0) {
                        // Validar límites
                        if (!checkDailyLimit(purchase.getConsumer().getId(), rule.getId())) {
                            log.warn("Daily limit reached for rule {} and consumer {}",
                                    rule.getId(), purchase.getConsumer().getId());
                            continue;
                        }

                        if (!checkTotalLimit(purchase.getConsumer().getId(), rule.getId())) {
                            log.warn("Total limit reached for rule {} and consumer {}",
                                    rule.getId(), purchase.getConsumer().getId());
                            continue;
                        }

                        // Emitir tickets para todas las rifas activas elegibles
                        issueTicketsForActiveRaffles(
                                purchase.getConsumer().getId(),
                                tickets,
                                RaffleTicketSource.PURCHASE,
                                purchase.getId(),
                                rule);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing rule {} for purchase {}",
                        rule.getId(), purchase.getId(), e);
                // Continuar con la siguiente regla
            }
        }
    }

    @Override
    public void onGameAchievement(Long consumerId, String achievementType, Integer value) {

        log.info("Processing game achievement: ConsumerId={}, Type={}, Value={}",
                consumerId, achievementType, value);

        // Obtener reglas activas de tipo GAME_ACHIEVEMENT
        List<TicketEarningRule> rules = getActiveRulesByType(RaffleTicketSource.GAME_ACHIEVEMENT);

        if (rules.isEmpty()) {
            log.info("No active GAME_ACHIEVEMENT rules found");
            return;
        }

        // Crear contexto para evaluación
        GameAchievementContext context = new GameAchievementContext(achievementType, value);

        for (TicketEarningRule rule : rules) {
            try {
                if (evaluateRule(rule, consumerId, RaffleTicketSource.GAME_ACHIEVEMENT, context)) {

                    Integer tickets = calculateTicketsForRule(rule, context);

                    if (tickets > 0 &&
                            checkDailyLimit(consumerId, rule.getId()) &&
                            checkTotalLimit(consumerId, rule.getId())) {

                        issueTicketsForActiveRaffles(
                                consumerId,
                                tickets,
                                RaffleTicketSource.GAME_ACHIEVEMENT,
                                null, // No hay sourceId específico
                                rule);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing rule {} for achievement {}",
                        rule.getId(), achievementType, e);
            }
        }
    }

    @Override
    public void onReferralCompleted(Long referrerId, Long referredId, Long purchaseId) {

        log.info("Processing referral completed: ReferrerId={}, ReferredId={}, PurchaseId={}",
                referrerId, referredId, purchaseId);

        // Obtener reglas activas de tipo REFERRAL
        List<TicketEarningRule> rules = getActiveRulesByType(RaffleTicketSource.REFERRAL);

        if (rules.isEmpty()) {
            log.info("No active REFERRAL rules found");
            return;
        }

        // Crear contexto
        ReferralContext context = new ReferralContext(referredId, purchaseId);

        for (TicketEarningRule rule : rules) {
            try {
                if (evaluateRule(rule, referrerId, RaffleTicketSource.REFERRAL, context)) {

                    Integer tickets = calculateTicketsForRule(rule, context);

                    if (tickets > 0 &&
                            checkDailyLimit(referrerId, rule.getId()) &&
                            checkTotalLimit(referrerId, rule.getId())) {

                        issueTicketsForActiveRaffles(
                                referrerId,
                                tickets,
                                RaffleTicketSource.REFERRAL,
                                purchaseId,
                                rule);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing rule {} for referral", rule.getId(), e);
            }
        }
    }

    @Override
    public void onAdWatched(Long consumerId, Long adId) {

        log.info("Processing ad watched: ConsumerId={}, AdId={}", consumerId, adId);

        // Obtener reglas activas de tipo ADS_WATCHED
        List<TicketEarningRule> rules = getActiveRulesByType(RaffleTicketSource.ADS_WATCHED);

        if (rules.isEmpty()) {
            log.info("No active ADS_WATCHED rules found");
            return;
        }

        // Crear contexto
        AdWatchedContext context = new AdWatchedContext(adId);

        for (TicketEarningRule rule : rules) {
            try {
                if (evaluateRule(rule, consumerId, RaffleTicketSource.ADS_WATCHED, context)) {

                    Integer tickets = calculateTicketsForRule(rule, context);

                    if (tickets > 0 &&
                            checkDailyLimit(consumerId, rule.getId()) &&
                            checkTotalLimit(consumerId, rule.getId())) {

                        issueTicketsForActiveRaffles(
                                consumerId,
                                tickets,
                                RaffleTicketSource.ADS_WATCHED,
                                adId,
                                rule);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing rule {} for ad watched", rule.getId(), e);
            }
        }
    }

    // ========== EVALUACIÓN Y CÁLCULO ==========

    @Override
    public boolean evaluateRule(
            TicketEarningRule rule,
            Long consumerId,
            RaffleTicketSource sourceType,
            Object context) {

        // Verificar que la regla está activa
        if (!rule.isActive()) {
            return false;
        }

        // Verificar que la regla está vigente (fechas)
        if (!rule.isCurrentlyValid()) {
            return false;
        }

        // Verificar que hay cupo global disponible
        if (!rule.hasGlobalCapacity()) {
            return false;
        }

        // Verificar que el tipo de regla coincide con el tipo de fuente
        if (rule.getRuleType() != convertSourceToRuleType(sourceType)) {
            return false;
        }

        // Verificar requisito de mascota
        if (rule.isRequiresPet()) {
            ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);
            if (!consumer.isHasPet()) {
                return false;
            }
        }

        // Evaluación específica según el tipo de contexto
        return switch (sourceType) {
            case PURCHASE -> evaluatePurchaseRule(rule, (Purchase) context);
            case GAME_ACHIEVEMENT -> evaluateGameAchievementRule(rule, (GameAchievementContext) context);
            case REFERRAL -> evaluateReferralRule(rule, (ReferralContext) context);
            case ADS_WATCHED -> evaluateAdWatchedRule(rule, (AdWatchedContext) context);
            default -> true; // Para otros tipos, aceptar por defecto
        };
    }

    @Override
    public Integer calculateTicketsForRule(TicketEarningRule rule, Object context) {

        // Si tiene multiplicador, calcular dinámicamente
        if (rule.getTicketsMultiplier() != null && context instanceof Purchase) {
            Purchase purchase = (Purchase) context;

            // Calcular: (amount / 1000) * multiplier
            BigDecimal amount = purchase.getTotal();
            BigDecimal divisor = new BigDecimal("1000");
            BigDecimal multiplier = rule.getTicketsMultiplier();

            int tickets = amount.divide(divisor, 0, RoundingMode.DOWN)
                    .multiply(multiplier)
                    .intValue();

            return Math.max(tickets, 0);
        }

        // Si no, retornar cantidad fija configurada
        return rule.getTicketsToAward();
    }

    // ========== VALIDACIONES DE LÍMITES ==========

    @Override
    public boolean checkDailyLimit(Long consumerId, Long ruleId) {

        TicketEarningRule rule = getTicketEarningRuleById(ruleId);

        // Si no tiene límite diario, permitir
        if (rule.getMaxTicketsPerUserPerDay() == null) {
            return true;
        }

        // Contar tickets emitidos hoy por esta regla
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        ZonedDateTime startOfDay = today.atStartOfDay(ZoneId.of("America/Bogota"));
        ZonedDateTime endOfDay = today.plusDays(1).atStartOfDay(ZoneId.of("America/Bogota"));

        // Query para contar tickets de hoy
        long ticketsToday = ticketRepository.countByTicketOwnerIdAndSourceAndIssuedAtBetween(
                consumerId,
                convertRuleTypeToSource(rule.getRuleType()),
                startOfDay,
                endOfDay);

        boolean withinLimit = ticketsToday < rule.getMaxTicketsPerUserPerDay();

        log.debug("Daily limit check: ConsumerId={}, RuleId={}, Today={}, Limit={}, Result={}",
                consumerId, ruleId, ticketsToday, rule.getMaxTicketsPerUserPerDay(), withinLimit);

        return withinLimit;
    }

    @Override
    public boolean checkTotalLimit(Long consumerId, Long ruleId) {

        TicketEarningRule rule = getTicketEarningRuleById(ruleId);

        // Si no tiene límite total, permitir
        if (rule.getMaxTicketsPerUserTotal() == null) {
            return true;
        }

        // Contar tickets totales emitidos por esta regla
        long totalTickets = ticketRepository.countByTicketOwnerIdAndSource(
                consumerId,
                convertRuleTypeToSource(rule.getRuleType()));

        boolean withinLimit = totalTickets < rule.getMaxTicketsPerUserTotal();

        log.debug("Total limit check: ConsumerId={}, RuleId={}, Total={}, Limit={}, Result={}",
                consumerId, ruleId, totalTickets, rule.getMaxTicketsPerUserTotal(), withinLimit);

        return withinLimit;
    }

    @Override
    public boolean checkRaffleEligibility(Long consumerId, RaffleType type) {

        // Rifas STANDARD: todos pueden participar
        if (type == RaffleType.STANDARD) {
            return true;
        }

        // Rifas PREMIUM: requieren mascota
        if (type == RaffleType.PREMIUM) {
            ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

            return consumer.isHasPet();
        }

        return false;
    }

    // ========== QUERIES ==========

    @Override
    public List<TicketEarningRule> getActiveRulesByType(RaffleTicketSource type) {
        RuleType ruleType = convertSourceToRuleType(type);
        return ruleRepository.findByRuleTypeAndIsActiveTrueOrderByPriorityDesc(ruleType);
    }

    @Override
    public void applyRule(TicketEarningRule rule, Long consumerId, String context) {
        // Este método parece redundante, pero lo implementamos por si acaso
        log.warn("applyRule() called - this method should probably be removed from interface");
        throw new UnsupportedOperationException("Use event listener methods instead");
    }

    // ========== MÉTODOS PRIVADOS AUXILIARES ==========

    /**
     * Emite tickets para todas las rifas activas donde el usuario es elegible
     */
    private void issueTicketsForActiveRaffles(
            Long consumerId,
            Integer tickets,
            RaffleTicketSource source,
            Long sourceId,
            TicketEarningRule rule) {
        // Obtener rifas activas
        List<Raffle> activeRaffles = raffleRepository.findByRaffleStatus(RaffleStatus.ACTIVE);

        if (activeRaffles.isEmpty()) {
            log.info("No active raffles found. Tickets not issued.");
            return;
        }

        int totalIssued = 0;

        for (Raffle raffle : activeRaffles) {
            try {
                // Verificar elegibilidad
                if (!checkRaffleEligibility(consumerId, raffle.getRaffleType())) {
                    log.debug("Consumer {} not eligible for raffle {} (type: {})",
                            consumerId, raffle.getId(), raffle.getRaffleType());
                    continue;
                }

                // Verificar si la regla aplica a este tipo de rifa
                if (rule.getAppliesToRaffleType() != null &&
                        rule.getAppliesToRaffleType() != raffle.getRaffleType()) {
                    log.debug("Rule {} does not apply to raffle type {}",
                            rule.getId(), raffle.getRaffleType());
                    continue;
                }

                // Emitir tickets
                IssueTicketRequestDTO request = new IssueTicketRequestDTO();
                request.setConsumerId(consumerId);
                request.setRaffleId(raffle.getId());
                request.setQuantity(tickets);
                request.setSource(source);
                request.setSourceId(sourceId);

                raffleTicketService.issueTickets(request);

                totalIssued += tickets;

                log.info("Issued {} tickets to consumer {} for raffle {} from source {}",
                        tickets, consumerId, raffle.getId(), source);

            } catch (LimitReachedException e) {
                log.warn("Limit reached for raffle {}: {}", raffle.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Error issuing tickets for raffle {}", raffle.getId(), e);
            }
        }

        // Incrementar contador de usos de la regla
        if (totalIssued > 0) {
            rule.incrementUsageCount();
            ruleRepository.save(rule);
        }

        log.info("Total tickets issued: {} across {} raffles", totalIssued, activeRaffles.size());
    }

    /**
     * Evalúa regla específica para compras
     */
    private boolean evaluatePurchaseRule(TicketEarningRule rule, Purchase purchase) {

        // Verificar monto mínimo
        if (rule.getMinPurchaseAmount() != null) {
            if (purchase.getTotal().compareTo(rule.getMinPurchaseAmount()) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evalúa regla específica para logros de juego
     */
    private boolean evaluateGameAchievementRule(TicketEarningRule rule, GameAchievementContext context) {

        // Verificar tipo de logro
        if (rule.getAchievementType() != null && !rule.getAchievementType().isBlank()) {
            if (!rule.getAchievementType().equalsIgnoreCase(context.achievementType())) {
                return false;
            }
        }

        // Verificar valor mínimo del logro
        if (rule.getMinAchievementValue() != null) {
            if (context.value() < rule.getMinAchievementValue()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evalúa regla específica para referidos
     */
    private boolean evaluateReferralRule(TicketEarningRule rule, ReferralContext context) {

        // verificar cuantas personas refiere el usuario para ganar los tickets

        return true;
    }

    /**
     * Evalúa regla específica para anuncios
     */
    private boolean evaluateAdWatchedRule(TicketEarningRule rule, AdWatchedContext context) {
        // Para anuncios, normalmente no hay condiciones adicionales
        // Solo verificar que se haya visto completo (esto se valida antes de llamar al
        // evento)
        return true;
    }

    /**
     * Convierte RaffleTicketSource a RuleType
     */
    private RuleType convertSourceToRuleType(RaffleTicketSource source) {
        return switch (source) {
            case PURCHASE -> RuleType.PURCHASE;
            case GAME_ACHIEVEMENT -> RuleType.GAME_ACHIEVEMENT;
            case REFERRAL -> RuleType.REFERRAL;
            case ADS_WATCHED -> RuleType.ADS_WATCHED;
            default -> RuleType.CUSTOM;
        };
    }

    /**
     * Convierte RuleType a RaffleTicketSource
     */
    private RaffleTicketSource convertRuleTypeToSource(RuleType ruleType) {
        return switch (ruleType) {
            case PURCHASE -> RaffleTicketSource.PURCHASE;
            case GAME_ACHIEVEMENT -> RaffleTicketSource.GAME_ACHIEVEMENT;
            case REFERRAL -> RaffleTicketSource.REFERRAL;
            default -> RaffleTicketSource.PLATFORM_GIFT;
        };
    }

    // ========== CONTEXT CLASSES ==========

    /**
     * Contexto para logros de juego
     */
    private record GameAchievementContext(String achievementType, Integer value) {
    }

    /**
     * Contexto para referidos
     */
    private record ReferralContext(Long referredId, Long purchaseId) {
    }

    /**
     * Contexto para anuncios vistos
     */
    private record AdWatchedContext(Long adId) {
    }

    @Override
    public EntityCreatedResponseDTO createTicketEarningRule(CreateRuleRequestDTO request) {

        // Validar que el nombre no existe
        if (ruleRepository.existsByRuleName(request.getRuleName())) {
            throw new DuplicateResourceException("Rule with name '" + request.getRuleName() + "' already exists");
        }
        
        // Validar condiciones según el tipo
        switch (request.getRuleType()) {
            case PURCHASE -> {
                if (request.getMinPurchaseAmount() == null) {
                    throw new InvalidRequestException("Min purchase amount is required for PURCHASE rules");
                }
            }

            case ADS_WATCHED -> {
                if (request.getMinAdsWatched() == null) {
                    throw new InvalidRequestException("Min ads watched is required for ADS_WATCHED rules");
                }
            }
            case GAME_ACHIEVEMENT -> {
                if (request.getAchievementType() == null || request.getAchievementType().isBlank()) {
                    throw new InvalidRequestException("Achievement type is required for GAME_ACHIEVEMENT rules");
                }
            }
            case REFERRAL -> {
                if (request.getReferralAddedQuantity() == null) {
                    throw new InvalidRequestException("Referral added quantity is required for REFERRAL rules");
                }
            }

            case CUSTOM -> {
                if (request.getCustomConditions() == null) {
                    throw new InvalidRequestException("Custom conditions are required for CUSTOM rules");
                }
            }
        }
        
        // Validar que ticketsMultiplier y ticketsToAward no estén ambos configurados
        if (request.getTicketsMultiplier() != null && request.getTicketsMultiplier().compareTo(BigDecimal.ZERO) > 0) {
            if (request.getTicketsToAward() != null && request.getTicketsToAward() > 1) {
                throw new InvalidRequestException("Cannot use both ticketsMultiplier and ticketsToAward > 1");
            }
        }

        TicketEarningRule rule = new TicketEarningRule();
        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setPriority(request.getPriority());
        rule.setTicketsToAward(request.getTicketsToAward());
        rule.setTicketsMultiplier(request.getTicketsMultiplier());
        rule.setAppliesToRaffleType(request.getAppliesToRaffleType());
        rule.setMinPurchaseAmount(request.getMinPurchaseAmount());
        rule.setMinAdsWatched(request.getMinAdsWatched());
        rule.setAchievementType(request.getAchievementType());
        rule.setMinAchievementValue(request.getMinAchievementValue());
        rule.setReferralAddedQuantity(request.getReferralAddedQuantity());
        rule.setCustomConditions(request.getCustomConditions());
        rule.setMaxTicketsPerUserPerDay(request.getMaxTicketsPerUserPerDay());
        rule.setMaxTicketsPerUserTotal(request.getMaxTicketsPerUserTotal());
        rule.setMaxUsesGlobal(request.getMaxUsesGlobal());
        rule.setRequiresPet(request.getRequiresPet());
        rule.setValidFrom(request.getValidFrom());
        rule.setValidUntil(request.getValidUntil());

        TicketEarningRule savedRule = ruleRepository.save(rule);

        return new EntityCreatedResponseDTO(savedRule.getId(), "Ticket earning rule created successfully", Instant.now());
    }

    @Override
    public EntityUpdatedResponseDTO updateTicketEarningRule(Long ruleId, UpdateRuleRequestDTO request) {
        
        // Validar que el nombre no existe
        if (ruleRepository.existsByRuleName(request.getRuleName())) {
            throw new DuplicateResourceException("Rule with name '" + request.getRuleName() + "' already exists");
        }
        
        // Validar condiciones según el tipo
        switch (request.getRuleType()) {
            case PURCHASE -> {
                if (request.getMinPurchaseAmount() == null) {
                    throw new InvalidRequestException("Min purchase amount is required for PURCHASE rules");
                }
            }

            case ADS_WATCHED -> {
                if (request.getMinAdsWatched() == null) {
                    throw new InvalidRequestException("Min ads watched is required for ADS_WATCHED rules");
                }
            }
            case GAME_ACHIEVEMENT -> {
                if (request.getAchievementType() == null || request.getAchievementType().isBlank()) {
                    throw new InvalidRequestException("Achievement type is required for GAME_ACHIEVEMENT rules");
                }
            }
            case REFERRAL -> {
                if (request.getReferralAddedQuantity() == null) {
                    throw new InvalidRequestException("Referral added quantity is required for REFERRAL rules");
                }
            }

            case CUSTOM -> {
                if (request.getCustomConditions() == null) {
                    throw new InvalidRequestException("Custom conditions are required for CUSTOM rules");
                }
            }
        }
        
        // Validar que ticketsMultiplier y ticketsToAward no estén ambos configurados
        if (request.getTicketsMultiplier() != null && request.getTicketsMultiplier().compareTo(BigDecimal.ZERO) > 0) {
            if (request.getTicketsToAward() != null && request.getTicketsToAward() > 1) {
                throw new InvalidRequestException("Cannot use both ticketsMultiplier and ticketsToAward > 1");
            }
        }

        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setPriority(request.getPriority());
        rule.setActive(request.getIsActive());
        rule.setTicketsToAward(request.getTicketsToAward());
        rule.setTicketsMultiplier(request.getTicketsMultiplier());
        rule.setAppliesToRaffleType(request.getAppliesToRaffleType());
        rule.setMinPurchaseAmount(request.getMinPurchaseAmount());
        rule.setMinAdsWatched(request.getMinAdsWatched());
        rule.setAchievementType(request.getAchievementType());
        rule.setMinAchievementValue(request.getMinAchievementValue());
        rule.setReferralAddedQuantity(request.getReferralAddedQuantity());
        rule.setCustomConditions(request.getCustomConditions());
        rule.setMaxTicketsPerUserPerDay(request.getMaxTicketsPerUserPerDay());
        rule.setMaxTicketsPerUserTotal(request.getMaxTicketsPerUserTotal());
        rule.setMaxUsesGlobal(request.getMaxUsesGlobal());
        rule.setRequiresPet(request.getRequiresPet());
        rule.setValidFrom(request.getValidFrom());
        rule.setValidUntil(request.getValidUntil());

        TicketEarningRule updatedRule = ruleRepository.save(rule);

        return new EntityUpdatedResponseDTO(updatedRule.getId(), "Ticket earning rule updated successfully", Instant.now());
    }

    @Override
    @SuppressWarnings("null")
    public void deleteTicketEarningRule(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        ruleRepository.delete(rule);
    }

    @Override
    public void activateTicketEarningRule(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        rule.setActive(true);
        ruleRepository.save(rule);
    }

    @Override
    public void deactivateTicketEarningRule(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        rule.setActive(false);
        ruleRepository.save(rule);
    }

    @Override
    public List<RuleResponseDTO> getTicketEarningRulesList(RuleType type, boolean isActive, Pageable pageable) {
        List<TicketEarningRule> rules = ruleRepository.findByRuleTypeAndIsActiveOrderByPriorityDesc(type, isActive, pageable);
        return rules.stream().map(ruleMapper::toRuleResponseDTO).toList();
    }

    @Override
    public RuleResponseDTO getTicketEarningRuleResponseDTOById(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        return ruleMapper.toRuleResponseDTO(rule);
    }


}