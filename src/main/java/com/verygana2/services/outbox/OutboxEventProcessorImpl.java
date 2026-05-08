package com.verygana2.services.outbox;

import java.time.Instant;
import java.util.Map;

import com.verygana2.models.OutboxEvent;
import com.verygana2.models.enums.OutboxStatus;
import com.verygana2.repositories.OutboxEventRepository;
import com.verygana2.services.interfaces.OutboxEventProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.verygana2.services.interfaces.raffles.TicketDeliveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessorImpl implements OutboxEventProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final TicketDeliveryService ticketDeliveryService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(OutboxEvent event) {

        event.setStatus(OutboxStatus.PROCESSING);
        outboxEventRepository.save(event);

        try {
            dispatch(event);

            event.setStatus(OutboxStatus.DONE);
            event.setProcessedAt(Instant.now());
            log.info("✅ Outbox event {} [{}] processed successfully",
                    event.getId(), event.getEventType());

        } catch (Exception e) {

            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);
            event.setLastError(truncate(e.getMessage(), 500));

            if (newRetryCount >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("❌ Outbox event {} [{}] FAILED after {} retries. Error: {}",
                        event.getId(), event.getEventType(), MAX_RETRIES, e.getMessage());
            } else {
                event.setStatus(OutboxStatus.PENDING);
                log.warn("⚠️ Outbox event {} [{}] retry {}/{}. Error: {}",
                        event.getId(), event.getEventType(),
                        newRetryCount, MAX_RETRIES, e.getMessage());
            }
        }

        outboxEventRepository.save(event);
    }

    // ==================== DISPATCH ====================

    private void dispatch(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {
            case "REFERRAL_COMPLETED" -> handleReferralCompleted(event);
            default -> throw new IllegalArgumentException(
                    "Unknown event type: " + event.getEventType());
        }
    }

    private void handleReferralCompleted(OutboxEvent event) throws Exception {
        Map<?, ?> payload = objectMapper.readValue(event.getPayload(), Map.class);

        Long referrerId = Long.valueOf(payload.get("referrerId").toString());
        Long referralId = Long.valueOf(payload.get("referralId").toString());

        log.info("Handling REFERRAL_COMPLETED: referrerId={}, referralId={}",
                referrerId, referralId);

        ticketDeliveryService.processTicketEarningForReferral(referrerId, referralId);
    }

    // ==================== UTILS ====================

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
