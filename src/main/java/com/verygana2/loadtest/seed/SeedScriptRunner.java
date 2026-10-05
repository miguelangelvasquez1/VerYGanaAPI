package com.verygana2.loadtest.seed;

import java.util.List;
import java.util.Map;

/**
 * Corre scripts SQL del classpath en una sola conexión, con variables de sesión previas.
 * Es una interfaz para poder probar {@link LoadTestSeeder} sin BD.
 */
public interface SeedScriptRunner {

    /**
     * @param sessionVariables variables de MySQL ({@code @nombre}) que se fijan antes de los
     *                         scripts; deben vivir en la misma conexión que ellos
     * @param scriptPaths      rutas del classpath, en el orden en que se ejecutan
     */
    void run(Map<String, Object> sessionVariables, List<String> scriptPaths);
}
