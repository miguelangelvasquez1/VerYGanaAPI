package com.verygana2.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guarda de CA-7: ningún fuente de producción puede llevar el dominio del CDN escrito a mano.
 * La URL pública sale siempre de {@code PublicUrlBuilder}, que lee {@code R2_CDN_DOMAIN}.
 */
@DisplayName("Dominio del CDN fijo en el código")
class NoHardcodedCdnDomainTest {

    private static final List<String> FORBIDDEN = List.of("cdn.verygana.com", "cdn-beta.verygana.com");

    @Test
    @DisplayName("ningún .java de src/main contiene el dominio del CDN")
    void mainSourcesHaveNoCdnDomain() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(NoHardcodedCdnDomainTest::containsForbiddenDomain)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(offenders)
                .as("Archivos con el dominio del CDN escrito a mano; usar PublicUrlBuilder")
                .isEmpty();
    }

    private static boolean containsForbiddenDomain(Path file) {
        try {
            String content = Files.readString(file);
            return FORBIDDEN.stream().anyMatch(content::contains);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + file, e);
        }
    }
}
