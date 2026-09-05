package com.verygana2.exceptions.adsExceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * La sesión de visualización superó el máximo de reanudaciones permitidas y quedó
 * INVALIDATED: ya no se puede retomar ese anuncio con esa sesión.
 *
 * <p>Se mapea a 410 (Gone) —y no a 400— a propósito: le da al front un
 * discriminador confiable por status code para saber que debe descartar el
 * anuncio actual y pedir el siguiente ({@code GET /adLike/next}), sin depender de
 * parsear el texto de "message". Es distinto del 429 de
 * {@link LimitReachedException} (límite diario de likes), que ocurre en el flujo
 * de like, no al pedir anuncio.
 */
@ResponseStatus(HttpStatus.GONE)
public class WatchSessionResumeLimitException extends RuntimeException {

    public WatchSessionResumeLimitException(String message) {
        super(message);
    }
}
