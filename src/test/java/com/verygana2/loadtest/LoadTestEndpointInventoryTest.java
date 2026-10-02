package com.verygana2.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventario de endpoints de la API contra los recorridos de k6.
 *
 * <p>Cruza los controllers (por reflexión, sin contexto de Spring) con
 * {@code stress-tests/inventory/endpoints.csv} y con los fuentes de {@code stress-tests/k6}.
 * El CSV tiene la cabecera {@code method,path,journey,exclusion}: o lleva un recorrido
 * (C0..C8, M, D, A, K) o una exclusión con motivo, nunca las dos cosas.
 */
@DisplayName("Inventario de endpoints de la prueba de carga")
class LoadTestEndpointInventoryTest {

    private static final Path INVENTORY = Path.of("stress-tests", "inventory", "endpoints.csv");
    private static final Path K6_DIR = Path.of("stress-tests", "k6");

    private static final Set<String> ALLOWED_REASONS = Set.of("admin", "webhook", "utilitario", "streaming");
    private static final Set<String> JOURNEYS =
            Set.of("C0", "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "M", "D", "A", "K");

    @Test
    @DisplayName("cada endpoint de un controller está clasificado (recorrido o exclusión)")
    void everyControllerEndpointIsClassified() {
        Set<String> inventory = inventoryByKey().keySet();

        Set<String> missing = new TreeSet<>(controllerEndpoints());
        missing.removeAll(inventory);

        assertThat(missing)
                .as("Endpoints de los controllers que faltan en stress-tests/inventory/endpoints.csv")
                .isEmpty();
        // Todas las filas deben tener recorrido o motivo, y solo uno de los dos.
        List<String> unclassified = readInventory().stream()
                .filter(r -> r.journey().isBlank() == r.exclusion().isBlank())
                .map(Row::key)
                .toList();
        assertThat(unclassified).as("Filas sin recorrido ni exclusión, o con las dos").isEmpty();
    }

    @Test
    @DisplayName("el inventario no tiene filas de endpoints que ya no existen")
    void noStaleRowsInInventory() {
        Set<String> real = controllerEndpoints();

        Set<String> stale = new TreeSet<>(inventoryByKey().keySet());
        stale.removeAll(real);

        assertThat(stale).as("Filas del CSV sin endpoint en los controllers").isEmpty();
        assertThat(readInventory()).as("Filas duplicadas")
                .hasSize(inventoryByKey().size());
    }

    @Test
    @DisplayName("las exclusiones usan solo motivos permitidos y los recorridos solo códigos válidos")
    void exclusionsUseAllowedReason() {
        for (Row row : readInventory()) {
            if (!row.exclusion().isBlank()) {
                assertThat(ALLOWED_REASONS).as("motivo de " + row.key()).contains(row.exclusion());
            } else {
                assertThat(JOURNEYS).as("recorrido de " + row.key()).contains(row.journey());
            }
        }
    }

    @Test
    @DisplayName("cada endpoint con recorrido aparece como 'METHOD /ruta' en algún fuente k6")
    void everyJourneyEndpointIsCalledFromK6() throws IOException {
        String k6Sources = readK6Sources();

        List<String> notCalled = readInventory().stream()
                .filter(r -> r.exclusion().isBlank())
                .map(Row::key)
                .filter(key -> !k6Sources.contains("'" + key + "'"))
                .toList();

        assertThat(notCalled)
                .as("Endpoints con recorrido que ningún fuente de stress-tests/k6 llama (nombre exacto entre comillas simples)")
                .isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    record Row(String method, String path, String journey, String exclusion) {
        String key() {
            return method + " " + path;
        }
    }

    private static List<Row> readInventory() {
        try {
            List<String> lines = Files.readAllLines(INVENTORY);
            assertThat(lines).as("cabecera").isNotEmpty();
            assertThat(lines.get(0)).isEqualTo("method,path,journey,exclusion");
            List<Row> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                assertThat(parts).as("columnas de: " + line).hasSize(4);
                rows.add(new Row(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim()));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Row> inventoryByKey() {
        return readInventory().stream().collect(
                Collectors.toMap(Row::key, r -> r, (a, b) -> a, LinkedHashMap::new));
    }

    private static String readK6Sources() throws IOException {
        if (!Files.isDirectory(K6_DIR)) {
            return "";
        }
        try (Stream<Path> files = Files.walk(K6_DIR)) {
            return files.filter(p -> p.toString().endsWith(".js"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    /** Todos los mapeos HTTP ("METHOD /ruta") de los controllers, con las rutas de clase y de método. */
    static Set<String> controllerEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<String> endpoints = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.verygana2")) {
            if (!(definition instanceof AnnotatedBeanDefinition)) {
                continue;
            }
            Class<?> controller = load(definition.getBeanClassName());
            if (isTestClass(controller)) {
                continue; // controllers de ejemplo dentro de los tests no son parte de la API
            }
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            List<String> prefixes = classMapping == null || classMapping.path().length == 0
                    ? List.of("")
                    : Arrays.asList(classMapping.path());
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                List<String> paths = mapping.path().length == 0 ? List.of("") : Arrays.asList(mapping.path());
                RequestMethod[] verbs = mapping.method().length == 0
                        ? new RequestMethod[] { null }
                        : mapping.method();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        for (RequestMethod verb : verbs) {
                            endpoints.add((verb == null ? "ANY" : verb.name()) + " " + join(prefix, path));
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static boolean isTestClass(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes");
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String join(String prefix, String path) {
        String joined = ("/" + prefix + "/" + path).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }
}
