package com.verygana2.exceptions;

/**
 * Excepción para errores de cifrado/descifrado de códigos sensibles
 * (códigos de reclamo de premios, licencias de productos, etc.)
 */
public class CodeEncryptionException extends RuntimeException {

    public CodeEncryptionException(String message) {
        super(message);
    }

    public CodeEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
