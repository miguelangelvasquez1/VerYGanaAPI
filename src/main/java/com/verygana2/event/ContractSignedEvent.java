package com.verygana2.event;

import com.verygana2.models.enums.commercial.ContractPurpose;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Publicado cuando un {@link com.verygana2.models.commercial.CommercialContract}
 * queda firmado. Permite que otros flujos (cambio de plan) reaccionen sin acoplar
 * {@link com.verygana2.services.commercial.ESignatureServiceImpl} a ellos directamente.
 */
@Getter
public class ContractSignedEvent extends ApplicationEvent {

    private final Long contractId;
    private final ContractPurpose purpose;

    public ContractSignedEvent(Object source, Long contractId, ContractPurpose purpose) {
        super(source);
        this.contractId = contractId;
        this.purpose = purpose;
    }
}
