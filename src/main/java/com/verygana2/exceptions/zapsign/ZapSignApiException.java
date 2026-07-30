package com.verygana2.exceptions.zapsign;

import lombok.Getter;

/** Excepción lanzada cuando ZapSign retorna un error o hay un problema de red. */
@Getter
public class ZapSignApiException extends RuntimeException {

    private final int zapSignStatusCode;

    public ZapSignApiException(String message, int zapSignStatusCode) {
        super(message);
        this.zapSignStatusCode = zapSignStatusCode;
    }
}
