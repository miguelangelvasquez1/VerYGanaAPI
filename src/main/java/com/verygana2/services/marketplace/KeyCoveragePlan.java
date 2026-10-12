package com.verygana2.services.marketplace;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuánto de una compra puede cubrirse con llaves, unidad por unidad (Contrato B 2.26,
 * 6.22.8). Cada unidad tiene su tope ordinario y, si su producto está en una promoción
 * especial vigente, un tope ampliado.
 *
 * Todos los montos van en centavos y son múltiplos del valor de una llave: no se cubre
 * con fracciones de llave.
 */
public final class KeyCoveragePlan {

    /**
     * @param promotionId      promoción que amplía el tope de esta unidad, o null.
     * @param limitCents       tope que rige: el de la promoción si aplica, si no el ordinario.
     * @param pctApplied       porcentaje al que corresponde {@code limitCents}.
     * @param promotionEndsAt  hasta cuándo rige la promoción; null si no hay.
     */
    public record Unit(
            Long productId,
            long priceCents,
            int ordinaryPct,
            long ordinaryLimitCents,
            Long promotionId,
            int pctApplied,
            long limitCents,
            ZonedDateTime promotionEndsAt) {

        /** Lo máximo que esta unidad puede tomar por encima del tope ordinario. */
        public long promotionExcessCapacityCents() {
            return limitCents - ordinaryLimitCents;
        }
    }

    /** Lo que le toca a una unidad de las llaves que el usuario decidió aplicar. */
    public record Allocation(long keysCents, long promotionExcessCents) {
    }

    private final List<Unit> units;

    public KeyCoveragePlan(List<Unit> units) {
        this.units = List.copyOf(units);
    }

    public List<Unit> units() {
        return units;
    }

    public long totalCents() {
        return units.stream().mapToLong(Unit::priceCents).sum();
    }

    /** Cobertura máxima autorizada para toda la compra. */
    public long maxCoverageCents() {
        return units.stream().mapToLong(Unit::limitCents).sum();
    }

    /** Parte de la cobertura máxima que solo existe por promociones especiales. */
    public long maxPromotionExcessCents() {
        return units.stream().mapToLong(Unit::promotionExcessCapacityCents).sum();
    }

    /**
     * Reparte entre las unidades las llaves que el usuario aplica. Primero llena los
     * topes ordinarios y solo después usa el margen de las promociones: así una promoción
     * no gasta presupuesto mientras la compra quepa en lo ordinario.
     *
     * @return una asignación por unidad, en el mismo orden de {@link #units()}.
     */
    public List<Allocation> allocate(long keysCents) {
        if (keysCents < 0 || keysCents > maxCoverageCents()) {
            throw new IllegalArgumentException("Llaves fuera del límite de la compra: " + keysCents
                    + " (máximo " + maxCoverageCents() + ")");
        }

        long[] ordinary = new long[units.size()];
        long[] excess = new long[units.size()];
        long remaining = keysCents;

        for (int i = 0; i < units.size() && remaining > 0; i++) {
            ordinary[i] = Math.min(remaining, units.get(i).ordinaryLimitCents());
            remaining -= ordinary[i];
        }
        for (int i = 0; i < units.size() && remaining > 0; i++) {
            excess[i] = Math.min(remaining, units.get(i).promotionExcessCapacityCents());
            remaining -= excess[i];
        }

        List<Allocation> allocations = new ArrayList<>(units.size());
        for (int i = 0; i < units.size(); i++) {
            allocations.add(new Allocation(ordinary[i] + excess[i], excess[i]));
        }
        return allocations;
    }
}
