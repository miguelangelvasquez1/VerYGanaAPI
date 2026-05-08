package com.verygana2.event;


import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReferralCompletedEvent extends ApplicationEvent {

    private final Long referrerId;  // quien refirió (recibe el ticket)
    private final Long referralId;  // el nuevo usuario (usado como sourceId)

    public ReferralCompletedEvent(Object source, Long referrerId, Long referralId) {
        super(source);
        this.referrerId = referrerId;
        this.referralId = referralId;
    }
}