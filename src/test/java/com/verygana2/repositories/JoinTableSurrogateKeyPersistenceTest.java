package com.verygana2.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.Category;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * CA-6 de la spec 003 sobre H2 (modo MySQL): Hibernate guarda, lee y reemplaza las colecciones de
 * las tablas de unión aunque estas tengan una columna {@code id} que ninguna entidad mapea.
 * Es H2, no MySQL, y la columna aquí es visible (H2 no tiene INVISIBLE): prueba que el SQL de
 * Hibernate nombra columnas. La prueba real contra MySQL es la tarea T8.
 * Cubre ConsumerDetails.categories y las dos colecciones de TargetAudience; no cubre
 * SurveyAnswer.selectedOptions, GameAssetDefinition.allowedMimeTypes ni las tres listas de
 * CommercialOnboarding (solo las verifica T8).
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:join-table-surrogate-key-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Tablas de unión con columna id no mapeada (integración H2)")
class JoinTableSurrogateKeyPersistenceTest {

    private static final List<String> TABLES = List.of(
            "consumer_preferences", "target_audience_categories", "target_audience_municipalities");

    @Autowired
    private EntityManager em;

    /** Deja las tablas como queda MySQL tras la migración: con una columna id autoincremental como llave. */
    @BeforeEach
    void addSurrogateKeyColumn() {
        for (String table : TABLES) {
            Number hasId = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE LOWER(table_name) = '" + table + "' AND LOWER(column_name) = 'id'")
                    .getSingleResult();
            if (hasId.intValue() > 0) {
                continue; // el DDL no es transaccional: lo dejó un test anterior de esta misma clase
            }
            Number keys = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.table_constraints"
                            + " WHERE LOWER(table_name) = '" + table + "' AND constraint_type = 'PRIMARY KEY'")
                    .getSingleResult();
            assertThat(keys.intValue())
                    .as("H2 ya creó una llave primaria en %s: el test no representa el esquema migrado", table)
                    .isZero();
            em.createNativeQuery("ALTER TABLE " + table + " ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY")
                    .executeUpdate();
        }
    }

    private long rows(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    private Category persistCategory(String name) {
        Category category = new Category();
        category.setName(name);
        em.persist(category);
        return category;
    }

    private Municipality persistMunicipality(String code) {
        Department department = em.find(Department.class, "63");
        if (department == null) {
            department = new Department();
            department.setCode("63");
            department.setName("Quindío");
            em.persist(department);
        }
        Municipality municipality = em.find(Municipality.class, code);
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode(code);
            municipality.setName("Municipio " + code);
            municipality.setDepartment(department);
            em.persist(municipality);
        }
        return municipality;
    }

    @Test
    @DisplayName("las preferencias de un consumidor se guardan y se leen, también la misma categoría dos veces")
    void consumerPreferencesAreSavedAndReadBack() {
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        Category a = persistCategory("pref-a");
        Category b = persistCategory("pref-b");
        consumer.setCategories(new ArrayList<>(List.of(a, b, a)));
        em.flush();
        em.clear();

        ConsumerDetails reloaded = em.find(ConsumerDetails.class, consumer.getId());

        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactlyInAnyOrder("pref-a", "pref-b", "pref-a");
        assertThat(rows("consumer_preferences")).isEqualTo(3);
    }

    @Test
    @DisplayName("las categorías y los municipios de una audiencia se guardan y se leen, con un par repetido")
    void targetAudienceCategoriesAndMunicipalitiesAreSavedAndReadBack() {
        Category category = persistCategory("aud-a");
        Municipality armenia = persistMunicipality("63001");
        Municipality calarca = persistMunicipality("63130");
        TargetAudience audience = TargetAudience.builder()
                .categories(new ArrayList<>(List.of(category, category)))
                .targetMunicipalities(new ArrayList<>(List.of(armenia, calarca, armenia)))
                .build();
        em.persist(audience);
        em.flush();
        em.clear();

        TargetAudience reloaded = em.find(TargetAudience.class, audience.getId());

        assertThat(reloaded.getCategories()).hasSize(2);
        assertThat(reloaded.getTargetMunicipalities()).extracting(Municipality::getCode)
                .containsExactlyInAnyOrder("63001", "63130", "63001");
        assertThat(rows("target_audience_categories")).isEqualTo(2);
        assertThat(rows("target_audience_municipalities")).isEqualTo(3);
    }

    @Test
    @DisplayName("reemplazar una colección deja solo las filas nuevas")
    void replacingACollectionLeavesOnlyTheNewRows() {
        Category old = persistCategory("old");
        Category fresh = persistCategory("fresh");
        Municipality armenia = persistMunicipality("63001");
        Municipality calarca = persistMunicipality("63130");
        TargetAudience audience = TargetAudience.builder()
                .categories(new ArrayList<>(List.of(old)))
                .targetMunicipalities(new ArrayList<>(List.of(armenia)))
                .build();
        em.persist(audience);
        em.flush();
        em.clear();

        TargetAudience managed = em.find(TargetAudience.class, audience.getId());
        managed.setCategories(new ArrayList<>(List.of(fresh)));
        managed.setTargetMunicipalities(new ArrayList<>(List.of(calarca)));
        em.flush();
        em.clear();

        TargetAudience reloaded = em.find(TargetAudience.class, audience.getId());
        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactly("fresh");
        assertThat(reloaded.getTargetMunicipalities()).extracting(Municipality::getCode).containsExactly("63130");
        assertThat(rows("target_audience_categories")).isEqualTo(1);
        assertThat(rows("target_audience_municipalities")).isEqualTo(1);
    }
}
