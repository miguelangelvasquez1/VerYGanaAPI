package com.verygana2.models.records;

/**
 * Las dos caras de la identidad contable del fondo de llaves, en centavos.
 *
 * Cada centavo que entró a KEYS_RESERVE (el 60% de cada depósito) está en
 * exactamente uno de estos sitios: llaves que un consumidor tiene, saldo que el
 * anunciante todavía no gastó, o presupuesto comprometido en algo vivo. Si los
 * sumandos no dan KEYS_RESERVE, hay dinero que entró o salió sin rastro contable.
 */
public record KeyBacking(
        long keysReserveCents,
        long keyLiabilityCents,
        long advertiserBalanceCents,
        long committedAdsCents,
        long committedSurveysCents,
        long committedBrandingCents,
        long committedCampaignsCents) {

    public long committedCents() {
        return committedAdsCents + committedSurveysCents
                + committedBrandingCents + committedCampaignsCents;
    }

    /** Todo lo que KEYS_RESERVE debería estar respaldando. */
    public long accountedCents() {
        return keyLiabilityCents + advertiserBalanceCents + committedCents();
    }

    /** Negativo = faltan fondos. Positivo = sobra respaldo sin explicar. */
    public long driftCents() {
        return keysReserveCents - accountedCents();
    }

    /** Menos de 100 significa llaves emitidas sin plata detrás. */
    public double backingPct() {
        // Sin llaves en circulación no hay nada que respaldar: 100, no 0, o toda
        // plataforma recién estrenada se vería como insolvente.
        return keyLiabilityCents == 0
                ? 100.0
                : (keysReserveCents * 100.0) / keyLiabilityCents;
    }

    public boolean isUnderBacked() {
        return keysReserveCents < keyLiabilityCents;
    }
}
