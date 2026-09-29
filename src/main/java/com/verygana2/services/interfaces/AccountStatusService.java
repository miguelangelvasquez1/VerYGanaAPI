package com.verygana2.services.interfaces;

import com.verygana2.models.enums.AccountStatus;

public interface AccountStatusService {

    /**
     * Único punto de entrada para cambiar el AccountStatus de un usuario ya
     * persistido. Valida la transición contra AccountStatus.canTransitionTo,
     * lanza InvalidStatusException si no es válida, y deja registro en
     * account_status_history.
     */
    void transition(Long userId, AccountStatus target, String reason, String actor);
}
