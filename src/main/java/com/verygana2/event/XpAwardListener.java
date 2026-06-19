package com.verygana2.event;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.verygana2.services.interfaces.levels.LevelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica el XP solicitado por {@link XpAwardRequestedEvent} una vez que la
 * transacción origen ha hecho commit. Si la concesión falla, se loguea y se
 * descarta — la transacción original ya está cerrada y no sufre rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XpAwardListener {

    private final LevelService levelService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onXpAwardRequested(XpAwardRequestedEvent event) {
        try {
            levelService.awardActivity(event.getConsumerId(), event.getActivityType());
            log.info("XP otorgado: consumerId={}, activity={}",
                    event.getConsumerId(), event.getActivityType());
        } catch (Exception e) {
            log.error("Error otorgando XP: consumerId={}, activity={}, error={}",
                    event.getConsumerId(), event.getActivityType(), e.getMessage(), e);
        }
    }
}