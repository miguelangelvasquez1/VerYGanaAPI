package com.verygana2.loadtest.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.security.ClaimCodeEncryptor;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.utils.ReferenceSeedScripts;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Sin BD: el {@link JdbcTemplate} es un falso que contesta conteos y filas pendientes de
 * cifrar, y el {@link SeedScriptRunner} registra qué se le pidió correr.
 */
@DisplayName("LoadTestSeeder - runner del sembrado")
class LoadTestSeederTest {

    private static final String AES = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String HMAC = "0000000000000000000000000000000000000000000000000000000000000003";
    private static final String PASSWORD = "LoadTest-Clave-Secreta-1!";
    private static final String HASH = "$2a$10$hash.falso.del.encoder.de.la.app";

    private final ProductCodeEncryptor productEncryptor = new ProductCodeEncryptor(AES, HMAC);
    private final ClaimCodeEncryptor claimEncryptor = new ClaimCodeEncryptor(AES, HMAC);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SeedScriptRunner runner = mock(SeedScriptRunner.class);
    private final LoadTestProperties properties = new LoadTestProperties();

    /** Estado de la "BD" falsa. */
    private final Map<String, Integer> usersByRole = new HashMap<>();
    private final Map<Long, Integer> markers = new HashMap<>();
    private int pendingStock;
    private int pendingPrizes;
    private int pendingDelivered;
    private final List<List<Object[]>> stockBatches = new ArrayList<>();
    private final List<List<Object[]>> prizeBatches = new ArrayList<>();
    private final List<List<Object[]>> deliveredBatches = new ArrayList<>();
    private final List<Object[]> markerInserts = new ArrayList<>();
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger seederLogger;

    @BeforeEach
    void setUp() {
        properties.getSeed().setUsers(1_000);
        properties.getSeed().setPassword(PASSWORD);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
        jdbc = mock(JdbcTemplate.class, invocation -> fakeJdbc(invocation.getMethod().getName(),
                invocation.getArguments()));

        seederLogger = (Logger) LoggerFactory.getLogger(LoadTestSeeder.class);
        logs.start();
        seederLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        seederLogger.detachAppender(logs);
    }

    @SuppressWarnings("unchecked")
    private Object fakeJdbc(String method, Object[] args) {
        String sql = args.length > 0 && args[0] instanceof String s ? s : "";
        switch (method) {
            case "queryForObject": {
                if (sql.contains("FROM users")) {
                    return usersByRole.getOrDefault((String) args[2], 0);
                }
                if (sql.contains("FROM audit_logs")) {
                    return markers.getOrDefault(((Number) args[3]).longValue(), 0);
                }
                return 0;
            }
            case "queryForList": {
                long lastId = ((Number) args[1]).longValue();
                int limit = ((Number) args[2]).intValue();
                int pending = sql.contains("product_stock") ? pendingStock
                        : sql.contains("purchase_items") ? pendingDelivered : pendingPrizes;
                String prefix = sql.contains("product_stock") ? "STOCK-"
                        : sql.contains("purchase_items") ? "DELIVERED-" : "PRIZE-";
                List<Map<String, Object>> rows = new ArrayList<>();
                for (long id = lastId + 1; id <= pending && rows.size() < limit; id++) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", id);
                    row.put("code", "RAW:" + prefix + id);
                    rows.add(row);
                }
                return rows;
            }
            case "batchUpdate": {
                List<Object[]> batch = (List<Object[]>) args[1];
                (sql.contains("product_stock") ? stockBatches
                        : sql.contains("purchase_items") ? deliveredBatches : prizeBatches).add(batch);
                return new int[batch.size()];
            }
            case "update": {
                markerInserts.add(java.util.Arrays.copyOfRange(args, 1, args.length));
                return 1;
            }
            default:
                return null;
        }
    }

    private LoadTestSeeder seeder() {
        return new LoadTestSeeder(properties, jdbc, runner, passwordEncoder, productEncryptor, claimEncryptor);
    }

    private void run() throws Exception {
        seeder().run(new DefaultApplicationArguments());
    }

    private void seedUsersAsInScenarioA() {
        usersByRole.put("CONSUMER", 940);
        usersByRole.put("COMMERCIAL", 50);
        usersByRole.put("GAME_DESIGNER", 5);
        usersByRole.put("ADMIN", 3);
        usersByRole.put("COMPLIANCE_OFFICER", 2);
    }

    @Test
    @DisplayName("con users = 0 no hace nada")
    void doesNothingWhenUsersIsZero() throws Exception {
        properties.getSeed().setUsers(0);

        run();

        verifyNoInteractions(runner, jdbc, passwordEncoder);
    }

    @Test
    @DisplayName("corre primero los seeds de referencia y luego los scripts, con los parámetros del plan")
    @SuppressWarnings("unchecked")
    void runsScriptsInOrderWithPlanParameters() throws Exception {
        run();

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<List<String>> scripts = ArgumentCaptor.forClass(List.class);
        verify(runner, Mockito.times(2)).run(vars.capture(), scripts.capture());

        // 1) referencia: sin variables, la lista compartida con DataSeeder
        assertThat(vars.getAllValues().get(0)).isEmpty();
        assertThat(scripts.getAllValues().get(0)).isEqualTo(ReferenceSeedScripts.all());

        // 2) volumen: scripts en orden y variables del plan
        assertThat(scripts.getAllValues().get(1)).isEqualTo(LoadTestSeeder.SCRIPTS);
        assertThat(LoadTestSeeder.SCRIPTS).isSorted();
        LoadTestSeedPlan plan = LoadTestSeedPlan.forTotalUsers(1_000);
        Map<String, Object> expected = new LinkedHashMap<>(plan.sessionVariables());
        expected.put("lt_password_hash", HASH);
        assertThat(vars.getAllValues().get(1)).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("el hash BCrypt sale del PasswordEncoder de la app, una sola vez, y la clave en claro no viaja")
    @SuppressWarnings("unchecked")
    void hashesPasswordWithAppEncoder() throws Exception {
        run();

        verify(passwordEncoder, Mockito.times(1)).encode(PASSWORD);
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(runner, Mockito.times(2)).run(vars.capture(), anyList());
        assertThat(vars.getAllValues().get(1)).containsEntry("lt_password_hash", HASH);
        assertThat(vars.getAllValues().stream().flatMap(m -> m.values().stream()).map(String::valueOf))
                .noneMatch(value -> value.contains(PASSWORD));
    }

    @Test
    @DisplayName("sin contraseña configurada falla sin sembrar")
    void failsWhenPasswordIsBlank() {
        properties.getSeed().setPassword(" ");

        assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loadtest.seed.password");
        verifyNoInteractions(runner);
    }

    @Test
    @DisplayName("cifra por lotes los placeholders RAW: de stock y de premios")
    void encryptsStockAndPrizePlaceholdersInBatches() throws Exception {
        pendingStock = 1_200;
        pendingPrizes = 30;

        run();

        assertThat(stockBatches).extracting(List::size).containsExactly(500, 500, 200);
        assertThat(prizeBatches).extracting(List::size).containsExactly(30);

        // Cada fila de stock: [codigo cifrado, hash HMAC, id]
        Object[] firstStock = stockBatches.get(0).get(0);
        assertThat(productEncryptor.decrypt((String) firstStock[0])).isEqualTo("STOCK-1");
        assertThat(firstStock[1]).isEqualTo(productEncryptor.hash("STOCK-1"));
        assertThat(firstStock[2]).isEqualTo(1L);
        assertThat((String) firstStock[0]).doesNotStartWith("RAW:");

        // Cada premio: [codigo cifrado, id], con la llave de premios
        Object[] firstPrize = prizeBatches.get(0).get(0);
        assertThat(claimEncryptor.decrypt((String) firstPrize[0])).isEqualTo("PRIZE-1");
        assertThat(firstPrize[1]).isEqualTo(1L);
    }

    @Test
    @DisplayName("cifra por lotes los delivered_code de las compras con la llave de productos")
    void encryptsDeliveredCodePlaceholdersInBatches() throws Exception {
        pendingDelivered = 700;

        run();

        assertThat(deliveredBatches).extracting(List::size).containsExactly(500, 200);
        // Cada fila: [codigo cifrado, id]; el service lo descifra con ProductCodeEncryptor
        Object[] first = deliveredBatches.get(0).get(0);
        assertThat(productEncryptor.decrypt((String) first[0])).isEqualTo("DELIVERED-1");
        assertThat(first[1]).isEqualTo(1L);
        assertThat((String) first[0]).doesNotStartWith("RAW:");
        assertThat(stockBatches).isEmpty();
        assertThat(prizeBatches).isEmpty();
    }

    @Test
    @DisplayName("si el plan ya está completo (conteos y marca) no vuelve a sembrar ni a cifrar")
    void skipsUsersAlreadySeeded() throws Exception {
        seedUsersAsInScenarioA();
        markers.put(1_000L, 1);
        pendingStock = 10;

        run();

        verify(runner, never()).run(anyMap(), anyList());
        assertThat(stockBatches).isEmpty();
        assertThat(markerInserts).isEmpty();
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("con los conteos completos pero sin la marca (corrida interrumpida) vuelve a correr los scripts idempotentes")
    void rerunsWhenMarkerIsMissing() throws Exception {
        seedUsersAsInScenarioA();

        run();

        verify(runner, Mockito.times(2)).run(anyMap(), anyList());
    }

    @Test
    @DisplayName("crecer de A a B solo agrega consumidores y comerciales: el personal interno no cambia")
    @SuppressWarnings("unchecked")
    void growingFromAToBOnlyAddsConsumersAndCommercials() throws Exception {
        seedUsersAsInScenarioA();
        markers.put(1_000L, 1);
        properties.getSeed().setUsers(10_000);

        run();

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(runner, Mockito.times(2)).run(vars.capture(), anyList());
        Map<String, Object> volume = vars.getAllValues().get(1);
        assertThat(volume).containsEntry("lt_consumers", 9_490).containsEntry("lt_commercials", 500)
                // el personal interno pedido es el mismo que ya existe: los scripts no insertan más
                .containsEntry("lt_designers", usersByRole.get("GAME_DESIGNER"))
                .containsEntry("lt_admins", usersByRole.get("ADMIN"))
                .containsEntry("lt_compliance", usersByRole.get("COMPLIANCE_OFFICER"));
        // Y deja su propia marca para el total de B
        assertThat(markerInserts).hasSize(1);
        assertThat(markerInserts.get(0)).contains(10_000);
    }

    @Test
    @DisplayName("el log solo lleva conteos: ni correos, ni la clave, ni su hash, ni los códigos")
    void logsOnlyCounts() throws Exception {
        pendingStock = 3;
        pendingPrizes = 2;

        run();

        assertThat(logs.list).isNotEmpty();
        List<String> messages = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).noneMatch(m -> m.contains(PASSWORD) || m.contains(HASH)
                || m.contains("@") || m.contains("STOCK-") || m.contains("PRIZE-") || m.contains("RAW:"));
        assertThat(messages).anyMatch(m -> m.contains("940") && m.contains("50"));
        assertThat(logs.list).allMatch(e -> e.getThrowableProxy() == null);
    }
}
