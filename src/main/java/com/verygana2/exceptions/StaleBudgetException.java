package com.verygana2.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Un aumento de presupuesto llegó con una capacidad esperada distinta de la actual del activo
 * (p. ej. {@code expectedMaxLikes} != {@code maxLikes}). Como esa capacidad solo la cambia un
 * aumento, significa que otro aumento ya se aplicó —un doble clic, un reintento tras perder la
 * respuesta, o la misma pantalla abierta en dos pestañas— y aplicar este cobraría dos veces.
 * No se reintenta: el cliente debe recargar el activo y decidir de nuevo.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class StaleBudgetException extends RuntimeException {

    public StaleBudgetException(String message) {
        super(message);
    }
}
