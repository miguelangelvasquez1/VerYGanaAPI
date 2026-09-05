package com.verygana2.models.finance.plans;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePlanCapability {

    Capability[] value();

    String commercialIdParam() default "commercialId";

    /**
     * Si es true, además de las capacidades declaradas exige que el wallet del comercial
     * (STANDARD/PREMIUM) no esté agotado. Marca solo los puntos de creación real de
     * activos nuevos (y la exportación de reportes) — nunca edición/consulta.
     */
    boolean requiresBudget() default false;

    /**
     * Si es true, bloquea la operación cuando la billetera del comercial (STANDARD/PREMIUM)
     * lleva agotada más del periodo de gracia de su plan (estado DORMANT). Marca los puntos
     * de <b>edición</b> de activos ya creados — nunca pausar/reactivar activos ya
     * financiados, ni la consulta.
     */
    boolean blockWhenDormant() default false;

    enum Capability {
        CAN_ADVERTISE,
        CAN_USE_GAMES,
        CAN_USE_SURVEYS,
        CAN_SELL_DIRECTLY,
        CAN_HAVE_PETS,
        CAN_PROMOTE_ALLY_PRODUCTS,
        CAN_EXPORT_REPORT,
        /** Métricas de rendimiento de anuncios, encuestas y campañas (Estándar y Premium). */
        CAN_VIEW_PERFORMANCE_METRICS,
        /** Métrica de visitas a la página oficial del empresario / "Remisión" (exclusiva Premium). */
        CAN_VIEW_PAGE_VISIT_METRICS,
        MAX_PRODUCTS,
        MAX_ADS,
        MAX_BRANDED_GAMES,
        MAX_SURVEYS
    }
}