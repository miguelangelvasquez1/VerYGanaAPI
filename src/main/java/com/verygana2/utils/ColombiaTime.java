package com.verygana2.utils;

import java.time.ZoneId;

/** Zona horaria de Colombia, compartida por todo lo que necesite calcular "hoy" en hora local. */
public final class ColombiaTime {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private ColombiaTime() {
    }
}
