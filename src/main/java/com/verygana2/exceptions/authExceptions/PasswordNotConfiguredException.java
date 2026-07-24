package com.verygana2.exceptions.authExceptions;

import org.springframework.security.authentication.DisabledException;

/**
 * Login rechazado porque el usuario aún no ha configurado su contraseña
 * (link de setup-password enviado por correo no completado). Subtipo de
 * DisabledException para que GlobalExceptionHandler pueda dar un mensaje
 * específico en vez del genérico de cuenta deshabilitada.
 */
public class PasswordNotConfiguredException extends DisabledException {

    public PasswordNotConfiguredException(String message) {
        super(message);
    }
}
