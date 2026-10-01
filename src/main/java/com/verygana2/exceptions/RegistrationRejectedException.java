package com.verygana2.exceptions;

import com.verygana2.models.enums.RegistrationRejectionReason;

public class RegistrationRejectedException extends RuntimeException {

    private final RegistrationRejectionReason reason;

    public RegistrationRejectedException(RegistrationRejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RegistrationRejectionReason getReason() {
        return reason;
    }
}
