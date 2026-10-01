package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.verygana2.models.finance.plans.Feature;
import com.verygana2.models.finance.plans.Feature.FeatureType;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para FeatureRepository.
 * No expone métodos custom: solo CRUD heredado de JpaRepository, más la
 * verificación del constraint unique sobre Feature.code.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:feature-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FeatureRepository (integración H2)")
class FeatureRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private FeatureRepository featureRepository;

    // ==================== HELPERS ====================

    private Feature newFeature(String code) {
        return Feature.builder()
                .code(code)
                .name(code)
                .type(FeatureType.BOOLEAN)
                .build();
    }

    // ==================== save / findById ====================

    @Nested
    @DisplayName("guardar y leer")
    class SaveAndRead {

        @Test
        @DisplayName("guarda un feature y lo recupera con sus campos correctos")
        void savesAndReadsFeature() {
            Feature saved = featureRepository.save(newFeature("CAN_ADVERTISE"));
            em.flush();
            em.clear();

            Optional<Feature> found = featureRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("CAN_ADVERTISE");
            assertThat(found.get().getName()).isEqualTo("CAN_ADVERTISE");
            assertThat(found.get().getType()).isEqualTo(FeatureType.BOOLEAN);
        }
    }

    // ==================== unique code ====================

    @Nested
    @DisplayName("constraint unique sobre code")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("lanza DataIntegrityViolationException al intentar duplicar un code")
        void throwsOnDuplicateCode() {
            featureRepository.saveAndFlush(newFeature("MAX_PRODUCTS"));

            assertThatThrownBy(() -> featureRepository.saveAndFlush(newFeature("MAX_PRODUCTS")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
