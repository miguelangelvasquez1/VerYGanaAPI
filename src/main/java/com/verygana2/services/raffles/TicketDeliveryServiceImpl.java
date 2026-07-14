package com.verygana2.services.raffles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningResult;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;
import com.verygana2.services.interfaces.raffles.TicketDeliveryService;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TicketDeliveryServiceImpl implements TicketDeliveryService {

    private final RaffleTicketService raffleTicketService;
    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleService raffleService;
    private final NotificationService notificationService;
    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LevelService levelService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TicketEarningResult processTicketEarningForPurchase(Long consumerId, Long purchaseId,
            Long purchaseAmountCents) {

        log.info("Processing ticket earning for purchase: ConsumerId={}, PurchaseId={}, Amount={}",
                consumerId, purchaseId, purchaseAmountCents);

        validateInputs(consumerId, purchaseId, purchaseAmountCents);

        List<Raffle> activeRaffles = getActiveRafflesOrderedByDrawDate();

        log.info("Found {} active raffles", activeRaffles.size());

        if (activeRaffles.isEmpty()) {
            log.info("No active raffles found. No tickets issued.");
            return TicketEarningResult.empty();
        }

        log.info("Active raffle IDs: {}",
                activeRaffles.stream().map(Raffle::getId).toList());

        TicketEarningResult result = processRaffles(
                activeRaffles,
                consumerId,
                purchaseId,
                purchaseAmountCents);

        // Log resultado final
        logResult(result, consumerId, purchaseAmountCents);

        return result;

    }

    private TicketEarningResult processRaffles(
            List<Raffle> raffles,
            Long consumerId,
            Long purchaseId,
            Long purchaseAmountCents) {

        TicketEarningResult result = new TicketEarningResult();

        if (raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                consumerId,
                RaffleTicketSource.PURCHASE,
                purchaseId)) {

            log.info("Tickets already issued for this purchase. Skipping...");
            return TicketEarningResult.empty();
        }

        for (Raffle raffle : raffles) {
            log.info("Processing raffle: {} (ID: {}, Type: {})",
                    raffle.getTitle(), raffle.getId(), raffle.getRaffleType());
            try {

                // Solo permite emitir tickets de una rifa activa encontrada
                if (processRaffle(raffle, consumerId, purchaseId, purchaseAmountCents, result)) {
                    log.info("Tickets awarded. Stopping further processing.");
                    break;
                }

            } catch (LimitReachedException e) {
                // Límite alcanzado - no es error grave, continuar con siguiente rifa
                log.warn("Limit reached for raffle {}: {}", raffle.getId(), e.getMessage());
                result.addError(raffle.getId(), raffle.getTitle(), e.getMessage());

            } catch (Exception e) {
                // Error inesperado - logear y continuar
                log.error("Error processing raffle {}: {}", raffle.getId(), e.getMessage(), e);
                result.addError(raffle.getId(), raffle.getTitle(), "Unexpected error: " + e.getMessage());
            }
        }

        return result;
    }

    private boolean processRaffle(
            Raffle raffle,
            Long consumerId,
            Long purchaseId,
            Long purchaseAmountCents,
            TicketEarningResult result) {

        log.debug("Processing raffle: {} (ID: {})", raffle.getTitle(), raffle.getId());

        log.info("Raffle {} has {} rules",
                raffle.getId(),
                raffle.getRaffleRules() != null ? raffle.getRaffleRules().size() : 0);

        if (raffle.getRaffleRules() != null && !raffle.getRaffleRules().isEmpty()) {
            raffle.getRaffleRules().forEach(rule -> log.info("   └─ Rule ID: {}, Type: {}, Active: {}, RaffleId: {}",
                    rule.getId(),
                    rule.getTicketEarningRule() != null ? rule.getTicketEarningRule().getRuleType() : "NULL",
                    rule.isActive(),
                    rule.getRaffle() != null ? rule.getRaffle().getId() : "NULL"));
        }
        // 1. Buscar configuración PURCHASE activa
        RaffleRule purchaseRule = findActivePurchaseRule(raffle);

        if (purchaseRule == null) {
            log.debug("Raffle {} has no active PURCHASE rule. Skipping.", raffle.getId());
            return false;
        }

        log.info("Found PURCHASE rule: ID={}, MinAmountCents={}, TicketsToAward={}",
                purchaseRule.getId(),
                purchaseRule.getTicketEarningRule().getMinPurchaseAmountCents(),
                purchaseRule.getTicketEarningRule().getTicketsToAward());

        // 2. Verificar elegibilidad del usuario para el tipo de rifa (sin lanzar
        // excepción)
        if (!raffleTicketService.canUserReceiveTickets(consumerId, raffle.getRaffleType())) {
            log.debug("User {} is not eligible for {} raffle {}. Skipping.",
                    consumerId, raffle.getRaffleType(), raffle.getId());
            return false;
        }

        // 3. Obtener la regla global
        TicketEarningRule rule = purchaseRule.getTicketEarningRule();

        boolean meetsConditions = validatePurchaseRuleConditions(rule, purchaseAmountCents);

        log.info("Purchase validation: AmountCents={}, MinRequiredCents={}, Meets conditions: {}",
                purchaseAmountCents,
                rule.getMinPurchaseAmountCents(),
                meetsConditions);

        // 4. Validar condiciones de la regla
        if (!meetsConditions) {
            log.debug("Purchase doesn't meet rule conditions for raffle {}. Skipping.", raffle.getId());
            return false;
        }

        // 5. Calcular tickets a otorgar
        int ticketsToAward = rule.getTicketsToAward();

        log.info("Attempting to award {} tickets", ticketsToAward);

        if (ticketsToAward <= 0) {
            log.debug("No tickets to award for raffle {}. Skipping.", raffle.getId());
            return false;
        }

        // 6. Emitir tickets
        log.info(
                "Calling raffleTicketService.issueTickets() with: consumerId={}, raffleId={}, quantity={}, source=PURCHASE, sourceId={}",
                consumerId, raffle.getId(), ticketsToAward, purchaseId);
        List<RaffleTicketResponseDTO> issuedTickets = raffleTicketService.issueTickets(
                consumerId,
                raffle.getId(),
                ticketsToAward,
                RaffleTicketSource.PURCHASE,
                purchaseId);

        // 7. Registrar éxito
        result.addSuccess(raffle.getId(), raffle.getTitle(), issuedTickets.size());

        log.info("Issued {} tickets to consumer {} for raffle {} ({})",
                issuedTickets.size(), consumerId, raffle.getId(), raffle.getTitle());

        // 8. Enviar notificación
        if (result.getTotalTicketsIssued() > 0) {
            sendNotification(consumerId, raffle, issuedTickets.size(), RaffleTicketSource.PURCHASE);
        }

        return true;

    }

    private void validateInputs(Long consumerId, Long purchaseId, Long purchaseAmountCents) {

        log.debug("Validating inputs: consumerId={}, purchaseId={}, amount={}",
                consumerId, purchaseId, purchaseAmountCents);

        if (consumerId == null || consumerId <= 0) {
            throw new InvalidRequestException("Consumer ID must be positive");
        }

        if (purchaseId == null || purchaseId <= 0) {
            throw new InvalidRequestException("Purchase ID must be positive");
        }

        if (purchaseAmountCents == null || purchaseAmountCents <= 0) {
            throw new InvalidRequestException("Purchase amount must be positive");
        }

        log.debug("Inputs validated successfully");
    }

    private boolean validatePurchaseRuleConditions(
            TicketEarningRule rule,
            Long purchaseAmount) {

        log.debug("Validating purchase rule conditions...");
        // 1. Validar monto mínimo
        if (rule.getMinPurchaseAmountCents() != null) {
            boolean meetsMinimum = purchaseAmount >= rule.getMinPurchaseAmountCents();

            log.debug(" Purchase amount: {}, Min required: {}, Meets: {}",
                    purchaseAmount, rule.getMinPurchaseAmountCents(), meetsMinimum);

            if (!meetsMinimum) {
                log.debug("Purchase amount {} is less than minimum {}",
                        purchaseAmount, rule.getMinPurchaseAmountCents());
                return false;
            }
        }
        log.debug("Rule conditions validated successfully");
        return true;
    }

    private List<Raffle> getActiveRafflesOrderedByDrawDate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        log.info("Fetching active raffles at {}", now);

        List<Raffle> raffles = raffleService.getActiveRafflesOrderedByDrawDate(now);
        log.info("Retrieved {} raffles from database", raffles.size());

        return raffles;
    }

    private RaffleRule findActivePurchaseRule(Raffle raffle) {
        log.debug("Searching for active PURCHASE rule in raffle {}", raffle.getId());

        if (raffle.getRaffleRules() == null) {
            log.warn("Raffle {} has NULL rules list", raffle.getId());
            return null;
        }

        RaffleRule result = raffle.getRaffleRules().stream()
                .filter(r -> {
                    boolean matches = r.getTicketEarningRule() != null &&
                            r.getTicketEarningRule().getRuleType() == TicketEarningRuleType.PURCHASE &&
                            r.isActive();

                    log.debug(" Checking rule ID {}: Type={}, Active={}, Matches: {}",
                            r.getId(),
                            r.getTicketEarningRule() != null ? r.getTicketEarningRule().getRuleType() : "NULL",
                            r.isActive(),
                            matches);

                    return matches;
                })
                .findFirst()
                .orElse(null);

        if (result != null) {
            log.debug("Found active PURCHASE rule: ID={}", result.getId());
        } else {
            log.debug("No active PURCHASE rule found for raffle {}", raffle.getId());
        }

        return result;
    }

    private void sendNotification(Long consumerId, Raffle raffle, int ticketsCount, RaffleTicketSource source) {
        try {
            String plural = ticketsCount > 1 ? "s" : "";
            String drawDate = raffle.getDrawDate().toLocalDate().toString();
            String raffleTitle = raffle.getTitle();

            String title;
            String message;

            switch (source) {
                case PURCHASE -> {
                    title = "¡Ganaste tickets por tu compra!";
                    message = String.format(
                            "Tu compra te dio %d ticket%s para la rifa '%s'. El sorteo es el %s. ¡Buena suerte!",
                            ticketsCount, plural, raffleTitle, drawDate);
                }
                case DAILY_LOGIN -> {
                    title = "¡Bonus diario desbloqueado!";
                    message = String.format(
                            "Por iniciar sesión hoy ganaste %d ticket%s para la rifa '%s'. El sorteo es el %s. ¡Vuelve mañana por más!",
                            ticketsCount, plural, raffleTitle, drawDate);
                }
                case REFERRAL -> {
                    title = "¡Gracias por referir a un amigo!";
                    message = String.format(
                            "Tu referido se unió a VerYGana y te ganaste %d ticket%s para la rifa '%s'. El sorteo es el %s. ¡Sigue refiriendo!",
                            ticketsCount, plural, raffleTitle, drawDate);
                }
                default -> {
                    title = "¡Ganaste tickets!";
                    message = String.format(
                            "Has ganado %d ticket%s para la rifa '%s'. El sorteo es el %s.",
                            ticketsCount, plural, raffleTitle, drawDate);
                }
            }

            notificationService.createInternalNotification(consumerId, title, message, Instant.now());
            log.info("Notification ({}) sent to consumer {}", source, consumerId);

        } catch (Exception e) {
            log.error("Failed to send notification to consumer {}: {}", consumerId, e.getMessage());
        }
    }

    private void logResult(TicketEarningResult result, Long consumerId, Long purchaseAmountCents) {
        log.info("Purchase processing completed for consumer {} (amount: ${}): " +
                "{} tickets issued across {} raffles ({} successful, {} failed)",
                consumerId,
                purchaseAmountCents,
                result.getTotalTicketsIssued(),
                result.getRafflesProcessed(),
                result.getRafflesSuccessful(),
                result.getRafflesFailed());

        if (!result.getErrors().isEmpty()) {
            log.warn("Errors occurred in {} raffles:", result.getRafflesFailed());
            result.getErrors().forEach((raffleId, error) -> log.warn("  - Raffle {}: {}", raffleId, error));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTicketEarningForDailyLogin(Long consumerId) {
        if (consumerId == null || consumerId <= 0)
            throw new InvalidRequestException("Consumer ID must be positive");

        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId).orElse(null);
        if (consumer == null) {
            log.debug("No ConsumerDetails for userId {}. Skipping daily login reward.", consumerId);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime last = consumer.getLastDailyLoginDate();

        if (last != null && last.toLocalDate().equals(now.toLocalDate())) {
            log.debug("Daily login ticket already awarded today for consumer {}", consumerId);
            return;
        }

        consumer.setLastDailyLoginDate(now);
        consumerDetailsRepository.save(consumer);

        List<Raffle> activeRaffles = getActiveRafflesOrderedByDrawDate();
        if (activeRaffles.isEmpty()) {
            log.info("No active raffles. No daily login tickets issued for consumer {}", consumerId);
            return;
        }

        for (Raffle raffle : activeRaffles) {
            try {
                if (processDailyLoginRaffle(raffle, consumerId)) {
                    log.info("Daily login ticket awarded in raffle {}. Stopping.", raffle.getId());
                    break;
                }
            } catch (LimitReachedException e) {
                log.warn("Limit reached in raffle {} for daily login: {}", raffle.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Error processing daily login in raffle {}: {}", raffle.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean processDailyLoginRaffle(Raffle raffle, Long consumerId) {
        RaffleRule dailyLoginRule = raffle.getRaffleRules() == null ? null
                : raffle.getRaffleRules().stream()
                        .filter(r -> r.getTicketEarningRule() != null
                                && r.getTicketEarningRule().getRuleType() == TicketEarningRuleType.DAILY_LOGIN
                                && r.isActive())
                        .findFirst()
                        .orElse(null);

        if (dailyLoginRule == null) {
            log.debug("Raffle {} has no active DAILY_LOGIN rule. Skipping.", raffle.getId());
            return false;
        }

        int ticketsToAward = dailyLoginRule.getTicketEarningRule().getTicketsToAward();
        if (ticketsToAward <= 0)
            return false;

        long todayId = Long.parseLong(LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE));
        List<RaffleTicketResponseDTO> issued = raffleTicketService.issueTickets(
                consumerId,
                raffle.getId(),
                ticketsToAward,
                RaffleTicketSource.DAILY_LOGIN,
                todayId);

        log.info("Issued {} daily login ticket(s) to consumer {} in raffle {}", issued.size(), consumerId,
                raffle.getId());

        if (!issued.isEmpty()) {
            sendNotification(consumerId, raffle, issued.size(), RaffleTicketSource.DAILY_LOGIN);
        }

        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTicketEarningForReferral(Long referrerId, Long referralId) {

        log.info("Processing referral ticket: referrerId={}, referralId={}",
                referrerId, referralId);

        if (referrerId == null || referrerId <= 0)
            throw new InvalidRequestException("Referrer ID must be positive");
        if (referralId == null || referralId <= 0)
            throw new InvalidRequestException("Referral ID must be positive");

        // Idempotencia: si ya se emitió ticket por este referido no repetir
        if (raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                referrerId, RaffleTicketSource.REFERRAL, referralId)) {
            log.info("✅ Referral ticket already exists for referrerId={}, referralId={}. Skipping.",
                    referrerId, referralId);
            return;
        }

        List<Raffle> activeRaffles = getActiveRafflesOrderedByDrawDate();

        if (activeRaffles.isEmpty()) {
            log.info("No active raffles for referral. Awarding double XP to referrer {}.", referrerId);
            eventPublisher.publishEvent(new XpAwardRequestedEvent(this, referrerId, ActivityType.REFERRAL_ACTIVE));
            return;
        }

        for (Raffle raffle : activeRaffles) {
            try {
                if (processReferralRaffle(raffle, referrerId, referralId)) {
                    log.info("Referral ticket awarded in raffle {}. Stopping.", raffle.getId());
                    break;
                }
            } catch (LimitReachedException e) {
                log.warn("Limit reached in raffle {} for referral: {}",
                        raffle.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Error processing referral in raffle {}: {}",
                        raffle.getId(), e.getMessage(), e);
                throw e; // relanzar para que el outbox reintente
            }
        }
    }

    private boolean processReferralRaffle(Raffle raffle, Long referrerId, Long referralId) {

        UserLevel userLevel = levelService.getUserLevel(referrerId);

        // BRONCE, PLATA y ORO solo participan en rifas STANDARD
        boolean canAccessPremium = userLevel.ordinal() >= UserLevel.RUBI.ordinal();
        if (!canAccessPremium && raffle.getRaffleType() == RaffleType.PREMIUM) {
            log.debug("User {} (level {}) is not eligible for PREMIUM raffle {}. Skipping.",
                    referrerId, userLevel, raffle.getId());
            return false;
        }

        RaffleRule referralRule = raffle.getRaffleRules() == null ? null
                : raffle.getRaffleRules().stream()
                        .filter(r -> r.getTicketEarningRule() != null
                                && r.getTicketEarningRule().getRuleType() == TicketEarningRuleType.REFERRAL
                                && r.isActive())
                        .findFirst()
                        .orElse(null);

        if (referralRule == null) {
            log.debug("Raffle {} has no active REFERRAL rule. Skipping.", raffle.getId());
            return false;
        }

        int ticketsToAward = userLevel.getReferralTickets();

        if (ticketsToAward <= 0) return false;

        List<RaffleTicketResponseDTO> issued = raffleTicketService.issueTickets(
                referrerId,
                raffle.getId(),
                ticketsToAward,
                RaffleTicketSource.REFERRAL,
                referralId);

        log.info("Issued {} referral ticket(s) to referrer {} (level {}) in raffle {}",
                issued.size(), referrerId, userLevel, raffle.getId());

        if (!issued.isEmpty()) {
            sendNotification(referrerId, raffle, issued.size(), RaffleTicketSource.REFERRAL);

            // Por cada ticket adicional más allá del primero, el nivel del referidor
            // recibe un evento REFERRAL_ACTIVE extra (el primero ya fue otorgado en applyReferredBy).
            int bonusXpEvents = issued.size() - 1;
            for (int i = 0; i < bonusXpEvents; i++) {
                eventPublisher.publishEvent(new XpAwardRequestedEvent(this, referrerId, ActivityType.REFERRAL_ACTIVE));
            }
            if (bonusXpEvents > 0) {
                log.info("Awarded {} bonus XP event(s) to referrer {} for {} tickets issued",
                        bonusXpEvents, referrerId, issued.size());
            }
        }

        return true;
    }

}
