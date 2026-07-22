package com.verygana2.exceptions.authExceptions;

import org.springframework.security.authentication.DisabledException;

/**
 * Login rechazado porque la cuenta está en revisión de cumplimiento/KYC
 * (UserState.PENDING_KYC_REVIEW). Subtipo de DisabledException para que
 * GlobalExceptionHandler pueda dar un mensaje específico en vez del
 * genérico de cuenta deshabilitada.
 */
public class PendingKycReviewException extends DisabledException {

    public PendingKycReviewException(String message) {
        super(message);
    }
}
