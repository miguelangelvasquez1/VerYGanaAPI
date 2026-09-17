package com.verygana2.models.records;

/**
 * Totales agregados de emisión de llaves pendientes de liquidar en tesorería.
 *
 * @param fundedCents   suma de lo que los anunciantes pagaron por las interacciones
 * @param creditedCents suma de lo que realmente se acreditó a los consumidores
 */
public record IssuanceTotals(long fundedCents, long creditedCents) {

    /** Positivo = sobrante (se emitió de menos). Negativo = déficit. */
    public long deltaCents() {
        return fundedCents - creditedCents;
    }

    public boolean isEmpty() {
        return fundedCents == 0 && creditedCents == 0;
    }
}
