package com.verygana2.loadtest.seed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Implementación contra MySQL: abre una conexión, fija las variables de sesión con
 * sentencias preparadas (el valor nunca se concatena al SQL) y corre cada script con
 * {@link ScriptUtils}, el mismo mecanismo que usa {@code DataSeeder}.
 */
@Component
@Profile("loadtest")
@RequiredArgsConstructor
public class JdbcSeedScriptRunner implements SeedScriptRunner {

    /** El generador de secuencias de los scripts es un CTE recursivo: hay que subir su tope. */
    private static final String RAISE_CTE_DEPTH = "SET SESSION cte_max_recursion_depth = 1000000";

    private final DataSource dataSource;

    @Override
    public void run(Map<String, Object> sessionVariables, List<String> scriptPaths) {
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute(RAISE_CTE_DEPTH);
            }
            for (Map.Entry<String, Object> variable : sessionVariables.entrySet()) {
                // El nombre sale del plan (identificador fijo), nunca de entrada externa.
                try (PreparedStatement set = connection.prepareStatement("SET @" + variable.getKey() + " = ?")) {
                    set.setObject(1, variable.getValue());
                    set.execute();
                }
            }
            for (String path : scriptPaths) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(path), "UTF-8"));
            }
        } catch (SQLException e) {
            // El mensaje propio solo lleva SQLState y código. La SQLException va como causa, así que su mensaje
            // sí sale en el stack trace: es aceptable porque los datos sembrados son ficticios (@loadtest.invalid).
            throw new IllegalStateException("Falló el sembrado de la prueba de carga (SQLState "
                    + e.getSQLState() + ", código " + e.getErrorCode() + ")", e);
        }
    }
}
