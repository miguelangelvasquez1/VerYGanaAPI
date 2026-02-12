package com.verygana2.services.raffles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningResult;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;
import com.verygana2.services.interfaces.raffles.TicketDeliveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TicketDeliveryServiceImpl implements TicketDeliveryService {

    private final RaffleTicketService raffleTicketService;
    private final RaffleService raffleService;
    private final NotificationService notificationService;

    @Override
    public TicketEarningResult processTicketEarningForPurchase(Long consumerId, Long purchaseId,
            BigDecimal purchaseAmount) {

        log.info("Processing ticket earning for purchase: ConsumerId={}, PurchaseId={}, Amount={}",
                consumerId, purchaseId, purchaseAmount);

        validateInputs(consumerId, purchaseId, purchaseAmount);

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
                purchaseAmount);

        // Log resultado final
        logResult(result, consumerId, purchaseAmount);

        return result;

    }

    private TicketEarningResult processRaffles(
            List<Raffle> raffles,
            Long consumerId,
            Long purchaseId,
            BigDecimal purchaseAmount) {

        TicketEarningResult result = new TicketEarningResult();

        for (Raffle raffle : raffles) {
            log.info("Processing raffle: {} (ID: {}, Type: {})",
                    raffle.getTitle(), raffle.getId(), raffle.getRaffleType());
            try {
                processRaffle(
                        raffle,
                        consumerId,
                        purchaseId,
                        purchaseAmount,
                        result);

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

    private void processRaffle(
            Raffle raffle,
            Long consumerId,
            Long purchaseId,
            BigDecimal purchaseAmount,
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
            return;
        }

        log.info("Found PURCHASE rule: ID={}, MinAmount={}, TicketsToAward={}",
                purchaseRule.getId(),
                purchaseRule.getTicketEarningRule().getMinPurchaseAmount(),
                purchaseRule.getTicketEarningRule().getTicketsToAward());

        // 2. Obtener la regla global
        TicketEarningRule rule = purchaseRule.getTicketEarningRule();

        boolean meetsConditions = validatePurchaseRuleConditions(rule, purchaseAmount);

        log.info("Purchase validation: Amount={}, MinRequired={}, Meets conditions: {}",
                purchaseAmount,
                rule.getMinPurchaseAmount(),
                meetsConditions);

        // 3. Validar condiciones de la regla
        if (!meetsConditions) {
            log.debug("Purchase doesn't meet rule conditions for raffle {}. Skipping.", raffle.getId());
            return;
        }

        // 4. Calcular tickets a otorgar
        int ticketsToAward = rule.getTicketsToAward();

        log.info("Attempting to award {} tickets", ticketsToAward);

        if (ticketsToAward <= 0) {
            log.debug("No tickets to award for raffle {}. Skipping.", raffle.getId());
            return;
        }

        // 5. Intentar emitir tickets
        try {
            log.info(
                    "Calling raffleTicketService.issueTickets() with: consumerId={}, raffleId={}, quantity={}, source=PURCHASE, sourceId={}",
                    consumerId, raffle.getId(), ticketsToAward, purchaseId);
            List<RaffleTicketResponseDTO> issuedTickets = raffleTicketService.issueTickets(
                    consumerId,
                    raffle.getId(),
                    ticketsToAward,
                    RaffleTicketSource.PURCHASE,
                    purchaseId);

            // 6. Registrar éxito
            result.addSuccess(raffle.getId(), raffle.getTitle(), issuedTickets.size());

            log.info("Issued {} tickets to consumer {} for raffle {} ({})",
                    issuedTickets.size(), consumerId, raffle.getId(), raffle.getTitle());

            // 7. Enviar notificación (asíncrona)
            sendNotification(consumerId, raffle, issuedTickets.size());

        } catch (Exception e) {
            log.error("Failed to issue tickets for raffle {}: {}", raffle.getId(), e.getMessage(), e); // ✅ LOG
                                                                                                         // AGREGADO
            throw e; // Re-lanzar para que sea capturado por processRaffles
        }

    }

    private void validateInputs(Long consumerId, Long purchaseId, BigDecimal purchaseAmount) {

        log.debug("Validating inputs: consumerId={}, purchaseId={}, amount={}",
                consumerId, purchaseId, purchaseAmount);

        if (consumerId == null || consumerId <= 0) {
            throw new InvalidRequestException("Consumer ID must be positive");
        }

        if (purchaseId == null || purchaseId <= 0) {
            throw new InvalidRequestException("Purchase ID must be positive");
        }

        if (purchaseAmount == null || purchaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Purchase amount must be positive");
        }

        log.debug("Inputs validated successfully");
    }

    private boolean validatePurchaseRuleConditions(
            TicketEarningRule rule,
            BigDecimal purchaseAmount) {

        log.debug("Validating purchase rule conditions...");
        // 1. Validar monto mínimo
        if (rule.getMinPurchaseAmount() != null) {
            boolean meetsMinimum = purchaseAmount.compareTo(rule.getMinPurchaseAmount()) >= 0;

            log.debug(" Purchase amount: {}, Min required: {}, Meets: {}",
                    purchaseAmount, rule.getMinPurchaseAmount(), meetsMinimum);

            if (!meetsMinimum) {
                log.debug("Purchase amount {} is less than minimum {}",
                        purchaseAmount, rule.getMinPurchaseAmount());
                return false;
            }
        }
        log.debug("Rule conditions validated successfully");
        return true;
    }

    private List<Raffle> getActiveRafflesOrderedByDrawDate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
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
                            r.getTicketEarningRule().getRuleType() == TicketEarningRuleType.PURCHASE;

                    log.debug(" Checking rule ID {}: Type={}, Matches PURCHASE: {}",
                            r.getId(),
                            r.getTicketEarningRule() != null ? r.getTicketEarningRule().getRuleType() : "NULL",
                            matches); // ✅ LOG AGREGADO

                    return matches;
                })
                .findFirst()
                .orElse(null);

        if (result != null) {
            log.debug("Found PURCHASE rule: ID={}", result.getId()); // ✅ LOG AGREGADO
        } else {
            log.warn("No PURCHASE rule found for raffle {}", raffle.getId()); // ✅ LOG AGREGADO
        }

        return result;
    }

    private void sendNotification(Long consumerId, Raffle raffle, int ticketsCount) {
        try {
            log.debug("📧 Preparing notification for consumer {}", consumerId);
            String title = "🎉 ¡Ganaste tickets para una rifa!";
            String message = String.format(
                    "Felicidades, has ganado %d ticket%s para la rifa '%s' que será sorteada el %s",
                    ticketsCount,
                    ticketsCount > 1 ? "s" : "",
                    raffle.getTitle(),
                    raffle.getDrawDate().toLocalDate());

            notificationService.createInternalNotification(
                    consumerId,
                    title,
                    message,
                    Instant.now());

            log.info("Notification sent to consumer {}", consumerId);

        } catch (Exception e) {
            // No fallar si la notificación falla
            log.error("Failed to send notification to consumer {}: {}", consumerId, e.getMessage());
        }
    }

    private void logResult(TicketEarningResult result, Long consumerId, BigDecimal purchaseAmount) {
        log.info("Purchase processing completed for consumer {} (amount: ${}): " +
                "{} tickets issued across {} raffles ({} successful, {} failed)",
                consumerId,
                purchaseAmount,
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
    public void processTicketEarningForAds(Long consumerId, Long adSessionId, Integer adsWatchedCount) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'processTicketEarningForAds'");
    }

    @Override
    public void processTicketEarningForGames(Long consumerId, Long gameId, Integer score) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'processTicketEarningForGames'");
    }

    @Override
    public void processTicketEarningForReferral(Long consumerId, Long referralId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'processTicketEarningForReferral'");
    }

}
