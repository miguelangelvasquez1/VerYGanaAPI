package com.verygana2.event;

import com.verygana2.models.enums.commercial.ContractPurpose;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Publicado cuando VerYGana rechaza un {@link com.verygana2.models.commercial.CommercialContract}.
 * Permite que otros flujos (cambio de plan) reaccionen sin acoplar
 * {@link com.verygana2.services.commercial.CommercialContractServiceImpl} a ellos directamente.
 */
@Getter
public class ContractRejectedEvent extends ApplicationEvent {

    private final Long contractId;
    private final ContractPurpose purpose;
    private final String reason;

    public ContractRejectedEvent(Object source, Long contractId, ContractPurpose purpose, String reason) {
        super(source);
        this.contractId = contractId;
        this.purpose = purpose;
        this.reason = reason;
    }
}
