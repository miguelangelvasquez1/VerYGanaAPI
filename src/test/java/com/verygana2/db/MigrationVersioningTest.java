package com.verygana2.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Vigila la regla de versionado de migraciones (CLAUDE.md, sección Base de datos): las V1-V12
 * son históricas y las nuevas llevan fecha. Una V13 correlativa quedaría "en el pasado" frente
 * a las versiones con fecha ya aplicadas y, con {@code out-of-order: false}, Flyway no arrancaría.
 */
@DisplayName("Versionado de migraciones Flyway")
class MigrationVersioningTest {

    private static final int LAST_PLAIN_VERSION = 12;
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    @DisplayName("no existe ninguna V<n> correlativa mayor que 12: las nuevas usan V<AAAAMMDDHHmm>")
    void noPlainVersionAboveTwelve() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            String name = r.getFilename();
            Matcher m = VERSION.matcher(name);
            if (m.matches() && m.group(1).length() < 12 && Long.parseLong(m.group(1)) > LAST_PLAIN_VERSION) {
                offenders.add(name);
            }
        }
        assertThat(offenders)
                .as("usa V<AAAAMMDDHHmm> (fecha y hora actuales) en vez de un correlativo mayor que V%d", LAST_PLAIN_VERSION)
                .isEmpty();
    }
}
