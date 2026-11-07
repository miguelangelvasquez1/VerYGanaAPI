package com.verygana2.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("BetaDataSeeder - seeds de referencia y demo")
class BetaDataSeederTest {

    private static final String BETA_DIR = "db/seed/beta/";

    @Test
    @DisplayName("carga la copia beta de cada seed base de dev, sin ejecutar antes de la wallet los ítems comerciales")
    void loadsBetaReferenceSeedsAndDefersWalletDependentPetItems() {
        List<String> scripts = BetaDataSeeder.seedScripts();

        // Cada seed base de dev (menos los ítems comerciales) debe tener su copia en db/seed/beta/.
        List<String> expectedBetaCopies = ReferenceSeedScripts.BASE_DATA.stream()
                .filter(path -> !path.equals(ReferenceSeedScripts.PET_COMMERCIAL_ITEMS))
                .map(path -> BETA_DIR + Path.of(path).getFileName())
                .toList();
        assertThat(scripts).containsAll(expectedBetaCopies);
        assertThat(scripts).containsAll(ReferenceSeedScripts.GAMES);

        // Ningún script de dev ni de test se cuela; solo beta/ y games/.
        assertThat(scripts).doesNotHaveDuplicates()
                .noneMatch(path -> path.startsWith("db/seed/test/"))
                .allMatch(path -> path.startsWith(BETA_DIR) || path.startsWith("db/seed/games/"));

        assertThat(scripts)
                .containsSubsequence(BETA_DIR + "departments.sql", BETA_DIR + "municipalities.sql")
                .containsSubsequence(BETA_DIR + "productCategories.sql", BETA_DIR + "beta_category_images.sql")
                .containsSubsequence(
                        BETA_DIR + "beta_users.sql",
                        BETA_DIR + "beta_finance.sql",
                        ReferenceSeedScripts.PET_COMMERCIAL_ITEMS);
    }

    @Test
    @DisplayName("todos los scripts de la lista existen en el classpath")
    void everyScriptExistsOnClasspath() {
        assertThat(BetaDataSeeder.seedScripts())
                .allSatisfy(path -> assertThat(new ClassPathResource(path).exists())
                        .as("existe %s", path).isTrue());
    }
}
