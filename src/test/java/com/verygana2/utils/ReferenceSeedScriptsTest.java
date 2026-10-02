package com.verygana2.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("ReferenceSeedScripts - lista compartida de seeds de referencia")
class ReferenceSeedScriptsTest {

    @Test
    @DisplayName("cada script de la lista existe en el classpath")
    void everyScriptExistsOnClasspath() {
        List<String> all = ReferenceSeedScripts.all();

        assertThat(all).isNotEmpty().doesNotHaveDuplicates();
        assertThat(all)
                .as("scripts que no están en el classpath")
                .filteredOn(path -> !new ClassPathResource(path).exists())
                .isEmpty();
    }

    @Test
    @DisplayName("los municipios se cargan después de los departamentos (FK)")
    void municipalitiesAfterDepartments() {
        List<String> all = ReferenceSeedScripts.all();

        assertThat(all).contains("db/seed/departments.sql", "db/seed/municipalities.sql");
        assertThat(all.indexOf("db/seed/departments.sql"))
                .isLessThan(all.indexOf("db/seed/municipalities.sql"));
    }

    @Test
    @DisplayName("solo datos de referencia: ningún script de usuarios de prueba")
    void containsNoTestEntities() {
        assertThat(ReferenceSeedScripts.all()).noneMatch(path -> path.startsWith("db/seed/test/"));
    }
}
