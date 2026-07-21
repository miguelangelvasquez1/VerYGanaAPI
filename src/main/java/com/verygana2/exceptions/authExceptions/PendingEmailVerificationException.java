package com.verygana2.exceptions.authExceptions;

import org.springframework.security.authentication.DisabledException;

/**
 * Login rechazado porque el usuario aún no ha verificado su correo
 * (UserState.PENDING_EMAIL). Subtipo de DisabledException para que
 * GlobalExceptionHandler pueda dar un mensaje específico en vez del
 * genérico de cuenta deshabilitada.
 */
public class PendingEmailVerificationException extends DisabledException {

    public PendingEmailVerificationException(String message) {
        super(message);
    }
}
