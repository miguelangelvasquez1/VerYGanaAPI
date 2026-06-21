package com.verygana2.exceptions.payoutExceptions;

public class PayoutMethodNotFoundException extends RuntimeException {
    public PayoutMethodNotFoundException(Long id) {
        super("Método de pago no encontrado: " + id);
    }
}
