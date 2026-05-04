package com.verygana2.exceptions.wompi;

import lombok.Getter;

/**
 * Excepción lanzada cuando Wompi retorna un error o hay un problema de red.
 * Incluye el HTTP status de Wompi para que el caller pueda decidir
 * si reintentar (5xx) o no (4xx).
 */
@Getter
public class WompiApiException extends RuntimeException {

    private final int wompiStatusCode;

    public WompiApiException(String message, int wompiStatusCode) {
        super(message);
        this.wompiStatusCode = wompiStatusCode;
    }

    /** true si el error es del lado de Wompi (reintentable) */
    public boolean isServerError() {
        return wompiStatusCode >= 500;
    }

    /** true si el error es de nuestra solicitud (no reintentable) */
    public boolean isClientError() {
        return wompiStatusCode >= 400 && wompiStatusCode < 500;
    }
}
