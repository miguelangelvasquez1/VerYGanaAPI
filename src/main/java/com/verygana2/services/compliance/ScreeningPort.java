package com.verygana2.services.compliance;

import java.util.List;

public interface ScreeningPort {

    /**
     * Consulta el nombre y documento contra todas las listas restrictivas configuradas.
     * Cada implementación decide qué listas incluye y cómo agota el timeout.
     */
    List<ScreeningOutcome> screen(String nombre, String documentoConsultado);
}