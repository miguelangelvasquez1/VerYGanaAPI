package com.verygana2.models.enums.finance;

/**
 * Tipo de asiento del libro de prosperidad (Contrato B 10.5, 10.6, 10.20, 10.27).
 * Cada tipo tiene una naturaleza fija de crédito (aumenta el Saldo) o débito (lo reduce).
 */
public enum ProsperityEntryType {
    /** Umbral generado por una Inversión Computable confirmada: neto × multiplicador. */
    THRESHOLD_GENERATED(true),
    /** Porción de una Venta Computable absorbida por el Saldo (no genera comisión). */
    SALE_ABSORPTION(false),
    /** Devolución al Saldo de lo absorbido por un ítem reembolsado. */
    REFUND_REINTEGRATION(true),
    /** Reversión de una Inversión Computable: retira del Saldo lo que aún quede de su Umbral. */
    THRESHOLD_REVERSAL(false),
    /** Ajuste compensatorio manual a favor del comercial, con causal obligatoria. */
    ADJUSTMENT_CREDIT(true),
    /** Ajuste compensatorio manual en contra del comercial, con causal obligatoria. */
    ADJUSTMENT_DEBIT(false);

    private final boolean credit;

    ProsperityEntryType(boolean credit) {
        this.credit = credit;
    }

    public boolean isCredit() {
        return credit;
    }

    /** Monto con signo según la naturaleza del asiento (positivo = crédito). */
    public long signedAmount(long amountCents) {
        return credit ? amountCents : -amountCents;
    }
}
