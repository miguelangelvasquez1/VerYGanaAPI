package com.verygana2.exceptions.authExceptions;

/**
 * Cuenta bloqueada por exceder el máximo de intentos fallidos de login.
 * Distinta de Spring Security's LockedException (reservada para cuentas
 * bloqueadas por compliance/admin) — esta requiere el código enviado por
 * correo para desbloquear, no una acción manual de soporte.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
