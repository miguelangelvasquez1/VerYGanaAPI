package com.verygana2.services.interfaces;

public interface TwilioSmsService {

    /**
     * Envía un código OTP vía SMS al número de teléfono indicado (Twilio Verify).
     * El número debe estar en formato colombiano de 10 dígitos (ej: 3001234567).
     */
    void sendOtp(String phoneNumber);

    /**
     * Verifica el código OTP ingresado por el usuario contra Twilio Verify.
     *
     * @return true si el código es correcto y no ha expirado, false en caso contrario.
     */
    boolean verifyOtp(String phoneNumber, String code);
}
