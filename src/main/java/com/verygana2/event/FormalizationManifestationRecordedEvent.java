package com.verygana2.event;

import org.springframework.context.ApplicationEvent;

import com.verygana2.models.enums.declarations.ManifestationStatus;

import lombok.Getter;

/**
 * Se registró qué pasó con la declaración de compromiso en la Hoja Final. La ausencia, la
 * negativa o una falla técnica abren una aclaración (4.7); nunca un rechazo automático.
 */
@Getter
public class FormalizationManifestationRecordedEvent extends ApplicationEvent {

    private final Long commercialId;
    private final Long manifestationId;
    private final ManifestationStatus status;

    public FormalizationManifestationRecordedEvent(Object source, Long commercialId, Long manifestationId,
            ManifestationStatus status) {
        super(source);
        this.commercialId = commercialId;
        this.manifestationId = manifestationId;
        this.status = status;
    }
}
