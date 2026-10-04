package com.verygana2.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * CA-5 de la spec 003: ninguna tabla creada por las migraciones puede quedar sin llave primaria
 * (DigitalOcean exige {@code sql_require_primary_key}). Lee las migraciones en orden de versión y
 * lleva el conjunto de tablas sin llave: un {@code CREATE TABLE} sin llave la agrega, un
 * {@code ALTER TABLE ... PRIMARY KEY} (también dentro del literal de un {@code PREPARE}) la quita.
 * No toca base de datos: es análisis de texto.
 */
@DisplayName("Llaves primarias en las migraciones Flyway")
class MigrationPrimaryKeyTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?(\\w+)`?\\s*(\\(|like\\b|as\\b|select\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile("alter\\s+table\\s+`?(\\w+)`?\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TABLE = Pattern.compile(
            "drop\\s+table\\s+(?:if\\s+exists\\s+)?`?(\\w+)`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIMARY_KEY = Pattern.compile("primary\\s+key", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_PRIMARY_KEY = Pattern.compile("drop\\s+primary\\s+key", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("toda tabla creada por las migraciones termina con llave primaria")
    void everyCreatedTableEndsUpWithPrimaryKey() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
        Arrays.sort(resources, Comparator.comparing(r -> versionOf(r.getFilename())));

        Map<String, String> withoutKey = new LinkedHashMap<>();
        for (Resource r : resources) {
            String sql = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            apply(sql, r.getFilename(), withoutKey);
        }

        assertThat(withoutKey)
                .as("tablas sin llave primaria al final de las migraciones (tabla=archivo que la dejó sin llave)")
                .isEmpty();
    }

    @Test
    @DisplayName("el parser marca una tabla creada sin llave primaria")
    void parserFlagsTableWithoutPrimaryKey() {
        Map<String, String> state = run("CREATE TABLE a (x bigint NOT NULL, y bigint NOT NULL);");
        assertThat(state).containsOnlyKeys("a");
        assertThat(state.get("a")).isEqualTo("test.sql");
    }

    @Test
    @DisplayName("el parser acepta la llave en la columna y la llave a nivel de tabla")
    void parserAcceptsColumnLevelAndTableLevelPrimaryKey() {
        assertThat(run("CREATE TABLE a (id bigint NOT NULL PRIMARY KEY, y int);")).isEmpty();
        assertThat(run("CREATE TABLE `b` (id bigint NOT NULL, y int, PRIMARY KEY (`id`));")).isEmpty();
    }

    @Test
    @DisplayName("el parser no se confunde con paréntesis dentro de enums y varchar")
    void parserIsNotConfusedByParenthesesInEnums() {
        String sql = "CREATE TABLE a (k enum('A','B') DEFAULT NULL, n varchar(5) NOT NULL, y int);"
                + " CREATE TABLE b (k enum('A(','B)') NOT NULL, n varchar(5) NOT NULL, PRIMARY KEY (n));";
        // a: sin llave (los paréntesis del enum y del varchar no la inventan); b: con llave
        assertThat(run(sql)).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("el parser acepta la llave que agrega un ALTER posterior, también dentro de un PREPARE")
    void parserAcceptsPrimaryKeyAddedByLaterAlter() {
        assertThat(run("CREATE TABLE a (x int); ALTER TABLE a ADD PRIMARY KEY (x);")).isEmpty();
        String prepared = "CREATE TABLE a (x int);"
                + " SET @ddl := IF(1=1, 'ALTER TABLE a ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY', 'SELECT 1');"
                + " PREPARE stmt FROM @ddl;";
        assertThat(run(prepared)).isEmpty();
        // DROP TABLE la quita; DROP PRIMARY KEY la vuelve a marcar
        assertThat(run("CREATE TABLE a (x int); DROP TABLE a;")).isEmpty();
        assertThat(run("CREATE TABLE a (x int PRIMARY KEY); ALTER TABLE a DROP PRIMARY KEY;")).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("el parser rechaza CREATE TABLE ... LIKE y AS SELECT: no se sabe si heredan la llave")
    void parserFlagsCreateTableLikeAndAsSelect() {
        assertThat(run("CREATE TABLE a LIKE b;")).containsOnlyKeys("a");
        assertThat(run("CREATE TABLE a AS SELECT * FROM b;")).containsOnlyKeys("a");
        assertThat(run("CREATE TABLE a SELECT * FROM b;")).containsOnlyKeys("a");
    }

    private static Map<String, String> run(String sql) {
        Map<String, String> state = new LinkedHashMap<>();
        apply(sql, "test.sql", state);
        return state;
    }

    /** Aplica un script al conjunto de tablas sin llave (tabla -> archivo que la dejó así). */
    private static void apply(String rawSql, String file, Map<String, String> withoutKey) {
        String sql = stripComments(rawSql);
        // El conjunto de eventos se ordena por posición en el texto para respetar el orden del script.
        TreeSet<Event> events = new TreeSet<>(Comparator.comparingInt(Event::pos));

        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase(Locale.ROOT);
            boolean hasKey = false;
            if ("(".equals(m.group(2))) {
                hasKey = PRIMARY_KEY.matcher(balancedBody(sql, m.end() - 1)).find();
            }
            events.add(new Event(m.start(), table, hasKey ? Kind.CREATE_WITH_KEY : Kind.CREATE_WITHOUT_KEY));
        }
        m = ALTER_TABLE.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase(Locale.ROOT);
            String clause = statementFrom(sql, m.end());
            if (DROP_PRIMARY_KEY.matcher(clause).find()) {
                events.add(new Event(m.start(), table, Kind.DROP_KEY));
            } else if (PRIMARY_KEY.matcher(clause).find()) {
                events.add(new Event(m.start(), table, Kind.ADD_KEY));
            }
        }
        m = DROP_TABLE.matcher(sql);
        while (m.find()) {
            events.add(new Event(m.start(), m.group(1).toLowerCase(Locale.ROOT), Kind.DROP_TABLE));
        }

        for (Event e : events) {
            switch (e.kind()) {
                case CREATE_WITHOUT_KEY, DROP_KEY -> withoutKey.put(e.table(), file);
                case CREATE_WITH_KEY, ADD_KEY, DROP_TABLE -> withoutKey.remove(e.table());
            }
        }
    }

    private enum Kind { CREATE_WITH_KEY, CREATE_WITHOUT_KEY, ADD_KEY, DROP_KEY, DROP_TABLE }

    private record Event(int pos, String table, Kind kind) { }

    /** Quita comentarios {@code -- ...}, {@code # ...} y {@code /* ... *}{@code /} fuera de los literales. */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        char quote = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < sql.length()) {
                    out.append(sql.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                i++;
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                out.append(c);
                i++;
            } else if (sql.startsWith("--", i) || c == '#') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (sql.startsWith("/*", i)) {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Devuelve el texto entre el paréntesis de {@code openIdx} y su pareja, contando paréntesis fuera de literales. */
    private static String balancedBody(String sql, int openIdx) {
        int depth = 0;
        char quote = 0;
        for (int i = openIdx; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return sql.substring(openIdx + 1, i);
            }
        }
        return sql.substring(openIdx + 1);
    }

    /**
     * Texto de la sentencia desde {@code from} hasta el siguiente {@code ;} fuera de literales. Si el ALTER está
     * dentro del literal de un PREPARE, llega hasta el cierre de ese literal (el {@code ;} del SET viene después).
     */
    private static String statementFrom(String sql, int from) {
        int end = from;
        while (end < sql.length() && sql.charAt(end) != ';' && sql.charAt(end) != '\'') {
            end++;
        }
        return sql.substring(from, end);
    }

    private static String versionOf(String filename) {
        Matcher m = VERSION.matcher(filename);
        if (!m.matches()) {
            throw new IllegalStateException("Migración con nombre inesperado: " + filename);
        }
        // Largo con ceros a la izquierda para comparar como número sin desbordar
        return String.format("%020d", Long.parseLong(m.group(1)));
    }
}
