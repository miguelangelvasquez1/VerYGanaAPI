package com.verygana2.loadtest.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Análisis estático de {@code db/loadtest/*.sql}: sin BD. Lo que no se puede ver leyendo el
 * texto (que las FK cuadren, que no haya ids repetidos) lo cubre la corrida contra el MySQL
 * local y {@code stress-tests/db/check-seed.sql}.
 */
@DisplayName("Scripts de sembrado db/loadtest - análisis estático")
class LoadTestSeedScriptsTest {

    private static final Pattern INSERT_HEAD = Pattern.compile(
            "^INSERT\\s+(IGNORE\\s+)?INTO\\s+(\\w+)\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Columnas de dinero que no terminan en {@code _cents} pero que en la entidad guardan
     * centavos (o COP enteros, según el modelo): se listan para que añadir una nueva obligue a
     * decidir su unidad.
     */
    private static final Set<String> MONEY_COLUMNS_IN_CENTS_BY_ENTITY = Set.of(
            "reward_per_like", "reward_amount", "amount", "credited_amount", "coins_earned",
            "amount_paid_cents", "unit_price_cents");

    /** Nombres que parecen dinero pero no lo son (factores, banderas). */
    private static final Set<String> NOT_MONEY = Set.of("score_reward_factor", "reward_granted", "last_budget_alert_stage",
            "commission_pct_applied", "sale_commission_pct_snapshot", "is_game_reward", "metric_value");

    /**
     * Única columna decimal en pesos que el sembrado escribe: {@code Prize.value} es
     * {@code BigDecimal} NOT NULL en la entidad y es el valor comercial del premio; no mueve
     * saldo de nadie. Cualquier otra columna decimal de dinero debe ir en centavos.
     */
    private static final Set<String> DECIMAL_PESOS_ALLOWED = Set.of("raffle_prizes.value");

    private static List<String> rawScripts() throws IOException {
        List<String> raw = new ArrayList<>();
        for (String path : LoadTestSeeder.SCRIPTS) {
            ClassPathResource resource = new ClassPathResource(path);
            assertThat(resource.exists()).as("falta el script %s", path).isTrue();
            raw.add(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        return raw;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
    }

    /** Sentencias sin comentarios. Ninguna sentencia lleva ';' dentro de un literal. */
    private static List<String> statements() throws IOException {
        List<String> statements = new ArrayList<>();
        for (String script : rawScripts()) {
            for (String part : stripComments(script).split(";")) {
                String statement = part.trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
            }
        }
        return statements;
    }

    @Test
    @DisplayName("los scripts son los siete esperados y existen en el classpath")
    void scriptsExist() throws IOException {
        assertThat(LoadTestSeeder.SCRIPTS).containsExactly(
                "db/loadtest/01-users.sql", "db/loadtest/02-wallets-levels.sql",
                "db/loadtest/03-commercial-assets.sql", "db/loadtest/04-consumer-preferences.sql",
                "db/loadtest/05-raffles.sql", "db/loadtest/06-history.sql", "db/loadtest/07-treasury-and-stories.sql");
        assertThat(rawScripts()).allSatisfy(script -> assertThat(script).isNotBlank());
    }

    @Test
    @DisplayName("cada INSERT es idempotente: INSERT IGNORE, ON DUPLICATE KEY o NOT EXISTS")
    void everyInsertIsIdempotent() throws IOException {
        List<String> inserts = statements().stream()
                .filter(s -> s.toUpperCase(Locale.ROOT).startsWith("INSERT")).toList();
        assertThat(inserts).as("el sembrado no tiene INSERT").hasSizeGreaterThan(20);

        assertThat(inserts).allSatisfy(insert -> {
            String upper = insert.toUpperCase(Locale.ROOT);
            boolean idempotent = INSERT_HEAD.matcher(insert).find() && (
                    upper.startsWith("INSERT IGNORE") || upper.contains("ON DUPLICATE KEY")
                            || upper.contains("NOT EXISTS"));
            assertThat(idempotent).as("INSERT no idempotente: %s", firstLine(insert)).isTrue();
        });
    }

    @Test
    @DisplayName("solo SET, INSERT y UPDATE: nada de DELETE, DROP, TRUNCATE ni ALTER")
    void onlyAdditiveStatements() throws IOException {
        assertThat(statements()).allSatisfy(statement -> assertThat(statement.toUpperCase(Locale.ROOT))
                .as("sentencia no permitida: %s", firstLine(statement))
                .matches("(?s)^(SET|INSERT|UPDATE)\\b.*"));
    }

    @Test
    @DisplayName("sin procedimientos, funciones, triggers, tablas temporales ni CREATE TABLE ... SELECT (GTID)")
    void noStoredProgramsOrTemporaryTables() throws IOException {
        for (String script : rawScripts()) {
            String upper = stripComments(script).toUpperCase(Locale.ROOT);
            assertThat(upper).doesNotContain("TEMPORARY").doesNotContainPattern("\\bCREATE\\b")
                    .doesNotContainPattern("\\bDELIMITER\\b").doesNotContainPattern("\\b(PROCEDURE|FUNCTION|TRIGGER)\\b");
        }
    }

    @Test
    @DisplayName("todo correo sembrado usa el dominio reservado .invalid")
    void emailsUseInvalidDomain() throws IOException {
        boolean consumerEmailFound = false;
        for (String script : rawScripts()) {
            for (String literal : stringLiterals(stripComments(script))) {
                if (!literal.contains("@") || literal.equals("@")) {
                    continue; // "@" solo es el separador de SUBSTRING_INDEX para sacar el número del correo
                }
                assertThat(literal).as("correo fuera de @loadtest.invalid").endsWith("@loadtest.invalid");
                consumerEmailFound |= literal.startsWith("lt-consumer-");
            }
        }
        assertThat(consumerEmailFound).as("no hay correos de consumidor en los scripts").isTrue();
        // Y los teléfonos van en el rango no asignado 399xxxxxxx
        assertThat(String.join("\n", rawScripts())).contains("'399'");
    }

    @Test
    @DisplayName("consumidores y anuncios reciben al menos una categoría (colecciones con @Size(min = 1))")
    void everyOwnerGetsAtLeastOneCategory() throws IOException {
        Map<String, String> inserts = insertsByTable();

        assertThat(inserts).containsKeys("consumer_preferences", "target_audience_categories");
        assertThat(LoadTestSeedPlan.forTotalUsers(1_000).multipliers().get("consumer_preferences"))
                .isGreaterThanOrEqualTo(1);
        // Las dos tablas se alimentan desde el dueño, no desde una lista fija que pueda quedar vacía
        assertThat(inserts.get("consumer_preferences")).contains("@lt_m_consumer_preferences");
        assertThat(inserts.get("target_audience_categories")).containsIgnoringCase("target_audiences");
    }

    @Test
    @DisplayName("el dinero va en columnas *_cents (o en las columnas en centavos conocidas); los decimales en pesos son una lista cerrada")
    void moneyGoesToCentsColumns() throws IOException {
        Pattern moneyLike = Pattern.compile(
                "(?i).*(amount|balance|price|cents|reward|budget|spent|commission|value|coins).*");
        for (String statement : statements()) {
            Matcher head = INSERT_HEAD.matcher(statement);
            if (!head.find()) {
                continue;
            }
            for (String column : head.group(3).split(",")) {
                String name = column.trim().replace("`", "").toLowerCase(Locale.ROOT);
                String qualified = head.group(2).toLowerCase(Locale.ROOT) + "." + name;
                if (moneyLike.matcher(name).matches() && !name.contains("_cents") && !NOT_MONEY.contains(name)
                        && !DECIMAL_PESOS_ALLOWED.contains(qualified)) {
                    assertThat(MONEY_COLUMNS_IN_CENTS_BY_ENTITY)
                            .as("columna de dinero sin sufijo _cents: %s", qualified)
                            .contains(name);
                }
            }
        }
    }

    @Test
    @DisplayName("la tesorería se fondea (KEYS_RESERVE, PAYOUTS_PENDING, OPERATIONS) en centavos y en proporción al escenario")
    void treasuryIsFundedProportionallyToScenario() throws IOException {
        String script = stripComments(new String(
                new ClassPathResource("db/loadtest/07-treasury-and-stories.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));

        assertThat(script).contains("treasury_accounts").contains("KEYS_RESERVE").contains("PAYOUTS_PENDING")
                .contains("OPERATIONS");
        // El saldo sale de los conteos del plan, no de una constante: A y B quedan proporcionales
        assertThat(script).contains("@lt_consumers").contains("@lt_commercials");
        // Idempotente: GREATEST no acumula al repetir (y deja intacto un saldo mayor)
        assertThat(script.toUpperCase(Locale.ROOT)).contains("GREATEST(");
        Set<String> multipliers = LoadTestSeedPlan.forTotalUsers(1_000).multipliers().keySet();
        assertThat(multipliers).contains("treasury_keys_reserve_cents_per_consumer",
                "treasury_payouts_pending_cents_per_commercial", "treasury_operations_cents_per_commercial");
    }

    @Test
    @DisplayName("las compras de historial llevan delivered_code como placeholder RAW: que el seeder cifra")
    void historyPurchasesCarryDeliveredCodePlaceholder() throws IOException {
        String history = insertsByTable().get("purchase_items");

        assertThat(history).isNotNull().contains("delivered_code").contains("'RAW:LT-DELIVERED-");
    }

    @Test
    @DisplayName("los refresh tokens se siembran como huella SHA-256 (token_hash), nunca con la columna token")
    void refreshTokensAreSeededAsHash() throws IOException {
        String refreshTokens = insertsByTable().get("refresh_tokens");

        assertThat(refreshTokens).isNotNull()
                .containsPattern("(?i)SHA2\\([^;]*,\\s*256\\)")
                .containsPattern("(?i)\\btoken_hash\\b")
                .doesNotContainPattern("(?i)[,(\\s]token\\s*[,)]");
    }

    @Test
    @DisplayName("hay historias de impacto PUBLISHED, globales, sin columnas de dinero decimal")
    void impactStoriesAreSeededAsPublished() throws IOException {
        String stories = insertsByTable().get("impact_stories");

        assertThat(stories).isNotNull().contains("'PUBLISHED'").contains("@lt_m_impact_stories");
        assertThat(stories).doesNotContain("invested_amount");
    }

    @Test
    @DisplayName("cada variable @lt_* que usan los scripts la fija el plan o el seeder")
    void everyVariableUsedIsProvided() throws IOException {
        Set<String> provided = new TreeSet<>(LoadTestSeedPlan.forTotalUsers(1_000).sessionVariables().keySet());
        provided.add("lt_password_hash");
        Set<String> assignedInScripts = new TreeSet<>();
        Set<String> used = new TreeSet<>();
        Pattern variable = Pattern.compile("@(lt_\\w+)");
        Pattern assignment = Pattern.compile("(?i)SET\\s+@(lt_\\w+)\\s*[:=]");
        for (String statement : statements()) {
            Matcher a = assignment.matcher(statement);
            while (a.find()) {
                assignedInScripts.add(a.group(1));
            }
            Matcher v = variable.matcher(statement);
            while (v.find()) {
                used.add(v.group(1));
            }
        }
        used.removeAll(provided);
        used.removeAll(assignedInScripts);
        assertThat(used).as("variables @lt_* sin definir").isEmpty();
    }

    private static Map<String, String> insertsByTable() throws IOException {
        Map<String, String> byTable = new LinkedHashMap<>();
        for (String statement : statements()) {
            Matcher head = INSERT_HEAD.matcher(statement);
            if (head.find()) {
                byTable.merge(head.group(2), statement, (a, b) -> a + "\n" + b);
            }
        }
        return byTable;
    }

    /** Literales de cadena de SQL (entre comillas simples, con '' como comilla escapada). */
    private static List<String> stringLiterals(String sql) {
        List<String> literals = new ArrayList<>();
        StringBuilder current = null;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (current == null) {
                if (ch == '\'') {
                    current = new StringBuilder();
                }
            } else if (ch == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    literals.add(current.toString());
                    current = null;
                }
            } else {
                current.append(ch);
            }
        }
        return literals;
    }

    private static String firstLine(String statement) {
        return statement.lines().findFirst().orElse(statement);
    }
}
