package com.verygana2.services.outbox;


import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.verygana2.models.OutboxEvent;
import com.verygana2.models.enums.OutboxStatus;
import com.verygana2.repositories.OutboxEventRepository;
import com.verygana2.services.interfaces.OutboxEventProcessor;
import com.verygana2.services.interfaces.OutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.verygana2.services.interfaces.raffles.TicketDeliveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final TicketDeliveryService ticketDeliveryService;
    private final ObjectMapper objectMapper;
    private final OutboxEventProcessor outboxEventProcessor;

    private static final int MAX_RETRIES = 3;
    private static final String REFERRAL_COMPLETED = "REFERRAL_COMPLETED";

    // ==================== GUARDAR EVENTO ====================

    /**
     * Se llama dentro de la transacción del registro.
     * Si algo falla, el rollback también deshace este save.
     */
    @Override
    @Transactional
    public void saveReferralEvent(Long referrerId, Long referralId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("referrerId", referrerId, "referralId", referralId));

            OutboxEvent event = OutboxEvent.builder()
                    .eventType(REFERRAL_COMPLETED)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.info("✅ Outbox event saved: referrerId={}, referralId={}",
                    referrerId, referralId);

        } catch (Exception e) {
            log.error("❌ Failed to save outbox event: {}", e.getMessage(), e);
        }
    }

    // ==================== PROCESAR EVENTOS ====================

    /**
     * Corre cada 30 segundos.
     * Toma hasta 10 eventos PENDING y los procesa uno a uno.
     */
    @Override
    @Scheduled(fixedDelay = 30_000)
    public void processOutboxEvents() {

        List<OutboxEvent> pending = outboxEventRepository
                .findTop10ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pending.isEmpty()) return;

        log.info("🔄 Processing {} pending outbox events", pending.size());

        for (OutboxEvent event : pending) {
            outboxEventProcessor.process(event);
        }
    }


    // ==================== LÓGICA INTERNA ====================

    /**
     * Procesa un evento individual en su propia transacción.
     * Si falla, solo ese evento queda marcado para reintento,
     * sin afectar a los demás.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSingleEvent(OutboxEvent event) {

        // Marcar como PROCESSING para evitar que otro hilo lo tome
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
                log.error("❌ Outbox event {} [{}] FAILED after {} retries. Last error: {}",
                        event.getId(), event.getEventType(), MAX_RETRIES, e.getMessage());
            } else {
                event.setStatus(OutboxStatus.PENDING);
                log.warn("⚠️ Outbox event {} [{}] failed, retry {}/{}. Error: {}",
                        event.getId(), event.getEventType(),
                        newRetryCount, MAX_RETRIES, e.getMessage());
            }
        }

        outboxEventRepository.save(event);
    }

    /**
     * Enruta el evento al handler correcto según su tipo.
     */
    private void dispatch(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {
            case REFERRAL_COMPLETED -> handleReferralCompleted(event);
            default -> {
                log.warn("Unknown event type: {}. Marking as FAILED.", event.getEventType());
                throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
            }
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

    /**
     * Evita guardar mensajes de error demasiado largos en BD.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}