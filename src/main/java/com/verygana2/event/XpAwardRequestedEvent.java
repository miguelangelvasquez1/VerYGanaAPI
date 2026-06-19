package com.verygana2.event;

import org.springframework.context.ApplicationEvent;

import com.verygana2.models.enums.ActivityType;

import lombok.Getter;

/**
 * Solicita otorgar XP al consumidor por una actividad confirmada.
 *
 * Se procesa con @TransactionalEventListener(AFTER_COMMIT), de modo que el
 * XP solo se concede si la transacción principal hizo commit exitoso.
 * Si la concesión de XP falla, la transacción original ya está cerrada y
 * no se ve afectada.
 */
@Getter
public class XpAwardRequestedEvent extends ApplicationEvent {

    private final Long consumerId;
    private final ActivityType activityType;

    public XpAwardRequestedEvent(Object source, Long consumerId, ActivityType activityType) {
        super(source);
        this.consumerId = consumerId;
        this.activityType = activityType;
    }
}