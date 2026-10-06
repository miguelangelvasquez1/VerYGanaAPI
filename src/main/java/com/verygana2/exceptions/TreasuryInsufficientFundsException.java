package com.verygana2.exceptions;

import com.verygana2.models.enums.finance.TreasuryAccountCode;

/**
 * Una cuenta de tesorería no cubre el movimiento que se le pide.
 *
 * Extiende {@link IllegalStateException} porque así se lanzaba antes y hay
 * llamadores que la atrapan con ese tipo (p. ej. {@code PayoutExecutionServiceImpl}).
 * La diferencia es que esta sí tiene handler: responde 409 en vez de caer al
 * catch-all como 500 "Unexpected error".
 *
 * El mensaje es detalle interno (puede llevar saldos) y va solo al log; al cliente
 * le llega la cuenta afectada, sin montos.
 */
public class TreasuryInsufficientFundsException extends IllegalStateException {

    private final TreasuryAccountCode account;

    public TreasuryInsufficientFundsException(TreasuryAccountCode account, String detail) {
        super(detail);
        this.account = account;
    }

    public TreasuryAccountCode getAccount() {
        return account;
    }
}
