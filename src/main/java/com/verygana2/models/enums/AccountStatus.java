package com.verygana2.models.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum AccountStatus {
    REGISTRATION_STARTED,
    PENDING_ACCEPTANCE,
    PENDING_VERIFICATION,
    PENDING_ACTIVATION,
    ACTIVE,
    PREVENTIVELY_RESTRICTED,
    SUSPENDED,
    TERMINATED;

    private static final Map<AccountStatus, Set<AccountStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private static Map<AccountStatus, Set<AccountStatus>> buildTransitions() {
        Map<AccountStatus, Set<AccountStatus>> transitions = new EnumMap<>(AccountStatus.class);
        transitions.put(REGISTRATION_STARTED, EnumSet.of(PENDING_ACCEPTANCE, SUSPENDED, TERMINATED));
        transitions.put(PENDING_ACCEPTANCE, EnumSet.of(PENDING_VERIFICATION, SUSPENDED, TERMINATED));
        transitions.put(PENDING_VERIFICATION, EnumSet.of(PENDING_ACTIVATION, ACTIVE, SUSPENDED, TERMINATED));
        transitions.put(PENDING_ACTIVATION, EnumSet.of(ACTIVE, SUSPENDED, TERMINATED));
        transitions.put(ACTIVE, EnumSet.of(PREVENTIVELY_RESTRICTED, SUSPENDED, TERMINATED));
        transitions.put(PREVENTIVELY_RESTRICTED, EnumSet.of(ACTIVE, SUSPENDED, TERMINATED));
        // SUSPENDED puede volver a cualquier estado no final del que pudo venir
        // (unblockUser restaura el estado previo a la última suspensión, no siempre ACTIVE),
        // más TERMINATED. Es decir: cualquier estado salvo sí mismo.
        Set<AccountStatus> fromSuspended = EnumSet.allOf(AccountStatus.class);
        fromSuspended.remove(SUSPENDED);
        transitions.put(SUSPENDED, fromSuspended);
        transitions.put(TERMINATED, EnumSet.noneOf(AccountStatus.class)); // final, sin salidas
        return transitions;
    }

    public boolean canTransitionTo(AccountStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
