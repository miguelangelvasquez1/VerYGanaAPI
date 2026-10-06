package com.verygana2.exceptions;

/**
 * KEYS_RESERVE no cubre un gasto de llaves. El mensaje llega al consumidor, así que
 * no lleva saldos ni montos: el detalle contable va al log de quien la lanza.
 */
public class KeysReserveInsufficientException extends RuntimeException {

    public KeysReserveInsufficientException() {
        super("El gasto de llaves no está disponible en este momento. Intenta de nuevo más tarde.");
    }
}
