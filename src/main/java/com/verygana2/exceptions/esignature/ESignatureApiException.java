package com.verygana2.exceptions.esignature;

import lombok.Getter;

/**
 * Excepción lanzada cuando el proveedor de firma electrónica retorna un error o hay
 * un problema de red. Incluye el HTTP status del proveedor para que el caller pueda
 * decidir si reintentar (5xx) o no (4xx).
 */
@Getter
public class ESignatureApiException extends RuntimeException {

    private final int providerStatusCode;

    public ESignatureApiException(String message, int providerStatusCode) {
        super(message);
        this.providerStatusCode = providerStatusCode;
    }

    /** true si el error es del lado del proveedor (reintentable) */
    public boolean isServerError() {
        return providerStatusCode >= 500;
    }

    /** true si el error es de nuestra solicitud (no reintentable) */
    public boolean isClientError() {
        return providerStatusCode >= 400 && providerStatusCode < 500;
    }
}
