package com.verygana2.exceptions;

/**
 * La recarga que se intentaba pagar de nuevo o cancelar en realidad ya estaba
 * pagada en Wompi (el webhook nunca llegó y se concilió en esta misma llamada).
 * Los métodos que la lanzan la declaran en {@code noRollbackFor}: la acreditación
 * del saldo hecha durante la conciliación debe persistir aunque la operación
 * pedida se rechace.
 */
public class RechargeAlreadyPaidException extends BusinessException {
    public RechargeAlreadyPaidException(String message) {
        super(message);
    }
}
