package com.verygana2.event;

import org.springframework.context.ApplicationEvent;

import com.verygana2.models.enums.tax.TaxProfileChangeType;

import lombok.Getter;

/**
 * El empresario cambió actividades o establecimientos (4.8). Sirve para abrir una revisión:
 * nunca cambia por sí solo la vocación, la comisión ni operaciones históricas.
 */
@Getter
public class TaxProfileChangedEvent extends ApplicationEvent {

    private final Long commercialId;
    private final TaxProfileChangeType changeType;
    private final String detail;

    public TaxProfileChangedEvent(Object source, Long commercialId, TaxProfileChangeType changeType, String detail) {
        super(source);
        this.commercialId = commercialId;
        this.changeType = changeType;
        this.detail = detail;
    }
}
