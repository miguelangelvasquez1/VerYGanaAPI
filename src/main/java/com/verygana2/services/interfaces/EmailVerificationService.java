package com.verygana2.services.interfaces;

public interface EmailVerificationService {

    /**
     * Genera un código de 6 dígitos, invalida cualquier código previo para ese
     * correo y lo envía por email. Válido tanto para verificar el correo de un
     * registro nuevo como para verificar un correo alternativo al reclamar un premio.
     */
    void sendVerificationCode(String email);

    /**
     * Verifica el código ingresado por el usuario contra el último código
     * generado para ese correo.
     *
     * @throws com.verygana2.exceptions.EmailVerificationException si no hay código pendiente,
     *         ya fue usado, expiró, se agotaron los intentos o el código no coincide.
     */
    void verifyCode(String email, String code);
}
