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

    enum Capability {
        CAN_ADVERTISE,
        CAN_USE_GAMES,
        CAN_USE_SURVEYS,
        CAN_SELL_DIRECTLY,
        CAN_HAVE_PETS,
        CAN_PROMOTE_ALLY_PRODUCTS,
        CAN_EXPORT_REPORT,
        MAX_PRODUCTS,
        MAX_ADS,
        MAX_BRANDED_GAMES,
        MAX_SURVEYS
    }
}