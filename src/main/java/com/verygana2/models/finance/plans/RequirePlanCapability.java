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

    enum Capability {
        CAN_ADVERTISE,
        CAN_USE_GAMES,
        CAN_USE_SURVEYS,
        MAX_PRODUCTS,
        MAX_ADS,
        MAX_BRANDED_GAMES,
        MAX_SURVEYS
    }
}