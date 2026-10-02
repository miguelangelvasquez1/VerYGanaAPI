package com.verygana2.loadtest.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.security.ClaimCodeEncryptor;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.utils.ReferenceSeedScripts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sembrado de volumen de la prueba de carga.
 *
 * <p>Con {@code loadtest.seed.users > 0}: (1) corre los seeds de referencia de
 * {@code db/seed} (la misma lista que usa {@code DataSeeder}); (2) corre los templates de
 * {@code db/loadtest} con los conteos y multiplicadores de {@link LoadTestSeedPlan};
 * (3) cifra en Java, por lotes, los placeholders {@code RAW:} de {@code product_stock},
 * {@code raffle_prizes} y {@code purchase_items.delivered_code} (AES + HMAC con la llave de la app, no se calculan en SQL); y
 * (4) deja una marca en {@code audit_logs} para no repetir el trabajo.
 *
 * <p>Es idempotente: los scripts insertan por llave natural o con {@code NOT EXISTS}, así que
 * correrlo dos veces, o crecer de A a B sobre la misma base, solo agrega lo que falta. El log
 * lleva únicamente conteos: nunca correos, claves, hashes ni códigos.
 *
 * <p>{@code @Order(50)}: después de los initializers de planes y tesorería (los scripts
 * necesitan los planes) y antes de {@code KeyIssuanceBackfillRunner} (100).
 */
@Slf4j
@Component
@Profile("loadtest")
@Order(50)
@RequiredArgsConstructor
public class LoadTestSeeder implements ApplicationRunner {

    /** Scripts de volumen, en el orden que exigen las FK. */
    static final List<String> SCRIPTS = List.of(
            "db/loadtest/01-users.sql",
            "db/loadtest/02-wallets-levels.sql",
            "db/loadtest/03-commercial-assets.sql",
            "db/loadtest/04-consumer-preferences.sql",
            "db/loadtest/05-raffles.sql",
            "db/loadtest/06-history.sql",
            "db/loadtest/07-treasury-and-stories.sql");

    static final String SEED_DONE_ACTION = "LOADTEST_SEED_COMPLETED";
    static final String RAW_PREFIX = "RAW:";
    static final int ENCRYPT_BATCH = 500;

    private static final String COUNT_USERS_SQL =
            "SELECT COUNT(*) FROM users WHERE role = ? AND email LIKE ?";
    private static final String COUNT_MARKER_SQL =
            "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND entity_id = ?";
    private static final String INSERT_MARKER_SQL =
            "INSERT INTO audit_logs (action, category, description, entity_type, entity_id, level, success, created_at) "
                    + "VALUES (?, 'LOADTEST', 'Sembrado de la prueba de carga completado', 'LOADTEST_SEED', ?, 'INFO', 1, NOW(6))";

    private static final String PENDING_STOCK_SQL =
            "SELECT id, code FROM product_stock WHERE id > ? AND code LIKE 'RAW:%' ORDER BY id LIMIT ?";
    private static final String UPDATE_STOCK_SQL =
            "UPDATE product_stock SET code = ?, code_hash = ? WHERE id = ?";
    private static final String PENDING_PRIZES_SQL =
            "SELECT id, claim_code AS code FROM raffle_prizes WHERE id > ? AND claim_code LIKE 'RAW:%' ORDER BY id LIMIT ?";
    private static final String UPDATE_PRIZE_SQL = "UPDATE raffle_prizes SET claim_code = ? WHERE id = ?";
    private static final String PENDING_DELIVERED_SQL =
            "SELECT id, delivered_code AS code FROM purchase_items WHERE id > ? AND delivered_code LIKE 'RAW:%' ORDER BY id LIMIT ?";
    private static final String UPDATE_DELIVERED_SQL = "UPDATE purchase_items SET delivered_code = ? WHERE id = ?";

    /** Rol de la BD y prefijo del correo sembrado, por orden de aparición en el log. */
    private static final Map<String, String> ROLE_EMAIL_SLUGS = rolesAndSlugs();

    private final LoadTestProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final SeedScriptRunner scriptRunner;
    private final PasswordEncoder passwordEncoder;
    private final ProductCodeEncryptor productCodeEncryptor;
    private final ClaimCodeEncryptor claimCodeEncryptor;

    @Override
    public void run(ApplicationArguments args) {
        int totalUsers = properties.getSeed().getUsers();
        if (totalUsers <= 0) {
            return;
        }
        String password = properties.getSeed().getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "loadtest.seed.password (LOADTEST_PASSWORD) es obligatoria para sembrar usuarios");
        }

        LoadTestSeedPlan plan = LoadTestSeedPlan.forTotalUsers(totalUsers);
        log.info("Plan de sembrado: total={}, consumidores={}, comerciales={}, diseñadores={}, admins={}, cumplimiento={}",
                plan.totalUsers(), plan.consumers(), plan.commercials(), plan.designers(), plan.admins(),
                plan.complianceOfficers());

        if (isComplete(plan)) {
            log.info("El sembrado de {} usuarios ya estaba completo: no se repite", totalUsers);
            return;
        }

        long startedAt = System.nanoTime();

        // 1) Datos de referencia (los mismos scripts que DataSeeder en dev).
        scriptRunner.run(Map.of(), ReferenceSeedScripts.all());

        // 2) Volumen: conteos y multiplicadores del plan + el hash BCrypt de la contraseña común.
        Map<String, Object> variables = new LinkedHashMap<>(plan.sessionVariables());
        variables.put("lt_password_hash", passwordEncoder.encode(password));
        scriptRunner.run(variables, SCRIPTS);

        // 3) Lo que no se puede calcular en SQL: AES + HMAC con la llave de la app.
        int stock = encryptPlaceholders(PENDING_STOCK_SQL, UPDATE_STOCK_SQL, true);
        int prizes = encryptPlaceholders(PENDING_PRIZES_SQL, UPDATE_PRIZE_SQL, false);
        int delivered = encryptDeliveredCodes();

        // 4) Marca de "terminado" para este total.
        jdbcTemplate.update(INSERT_MARKER_SQL, SEED_DONE_ACTION, totalUsers);

        long seconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
        log.info("Sembrado terminado en {} s: usuarios por rol (BD) = {}; códigos de stock cifrados = {}; "
                + "códigos de premios cifrados = {}; códigos entregados cifrados = {}", seconds, countsByRole(), stock,
                prizes, delivered);
    }

    /** Completo = los conteos por rol alcanzan el plan y quedó la marca de este total. */
    private boolean isComplete(LoadTestSeedPlan plan) {
        Map<String, Integer> planned = Map.of(
                "CONSUMER", plan.consumers(),
                "COMMERCIAL", plan.commercials(),
                "GAME_DESIGNER", plan.designers(),
                "ADMIN", plan.admins(),
                "COMPLIANCE_OFFICER", plan.complianceOfficers());
        for (Map.Entry<String, Integer> entry : planned.entrySet()) {
            if (countUsers(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        Integer marks = jdbcTemplate.queryForObject(COUNT_MARKER_SQL, Integer.class, SEED_DONE_ACTION,
                plan.totalUsers());
        return marks != null && marks > 0;
    }

    private int countUsers(String role) {
        String slug = ROLE_EMAIL_SLUGS.get(role);
        Integer count = jdbcTemplate.queryForObject(COUNT_USERS_SQL, Integer.class, role,
                "lt-" + slug + "-%@loadtest.invalid");
        return count == null ? 0 : count;
    }

    private Map<String, Integer> countsByRole() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ROLE_EMAIL_SLUGS.keySet().forEach(role -> counts.put(role, countUsers(role)));
        return counts;
    }

    /**
     * Cifra por lotes las filas cuyo código empieza por {@code RAW:}. Pagina por id (no por
     * offset) para que cada lote sea una lectura por índice aunque la tabla tenga cientos de
     * miles de filas.
     *
     * @param withHash {@code true} para stock de productos (código cifrado + hash HMAC),
     *                 {@code false} para premios (solo código cifrado)
     * @return filas cifradas
     */
    private int encryptPlaceholders(String selectSql, String updateSql, boolean withHash) {
        int total = 0;
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, lastId, ENCRYPT_BATCH);
            if (rows.isEmpty()) {
                return total;
            }
            List<Object[]> batch = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                String plain = ((String) row.get("code")).substring(RAW_PREFIX.length());
                if (withHash) {
                    batch.add(new Object[] {productCodeEncryptor.encrypt(plain), productCodeEncryptor.hash(plain), id});
                } else {
                    batch.add(new Object[] {claimCodeEncryptor.encrypt(plain), id});
                }
                lastId = id;
            }
            jdbcTemplate.batchUpdate(updateSql, batch);
            total += batch.size();
        }
    }

    /**
     * Cifra los {@code delivered_code} de las compras de historial. {@code PurchaseItemServiceImpl}
     * los descifra con {@link ProductCodeEncryptor}, por eso se usa esa llave (y no la de premios).
     */
    private int encryptDeliveredCodes() {
        int total = 0;
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(PENDING_DELIVERED_SQL, lastId, ENCRYPT_BATCH);
            if (rows.isEmpty()) {
                return total;
            }
            List<Object[]> batch = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                String plain = ((String) row.get("code")).substring(RAW_PREFIX.length());
                batch.add(new Object[] {productCodeEncryptor.encrypt(plain), id});
                lastId = id;
            }
            jdbcTemplate.batchUpdate(UPDATE_DELIVERED_SQL, batch);
            total += batch.size();
        }
    }

    private static Map<String, String> rolesAndSlugs() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("CONSUMER", "consumer");
        roles.put("COMMERCIAL", "commercial");
        roles.put("GAME_DESIGNER", "designer");
        roles.put("ADMIN", "admin");
        roles.put("COMPLIANCE_OFFICER", "compliance");
        return roles;
    }
}
