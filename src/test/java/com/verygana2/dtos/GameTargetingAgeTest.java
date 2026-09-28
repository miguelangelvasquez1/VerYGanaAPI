package com.verygana2.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.branding.UpdateBrandingRequestConfigDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * La segmentación por edad de los juegos va de 18 a 100: la plataforma ya no admite
 * menores. Aplica a la solicitud de brandeo y a la campaña; anuncios, encuestas,
 * productos y rifas tienen sus propias reglas.
 */
class GameTargetingAgeTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }

    @Nested
    @DisplayName("solicitud de brandeo")
    class BrandingRequest {

        private UpdateBrandingRequestConfigDTO config(int min, int max) {
            UpdateBrandingRequestConfigDTO dto = new UpdateBrandingRequestConfigDTO();
            dto.setMinAge(min);
            dto.setMaxAge(max);
            return dto;
        }

        @Test
        @DisplayName("18 a 100 pasa")
        void adultRangePasses() {
            assertThat(invalidFields(validator.validate(config(18, 100)))).isEmpty();
        }

        @Test
        @DisplayName("17 ya no: ni como mínima ni como máxima")
        void underageIsRejected() {
            assertThat(invalidFields(validator.validate(config(17, 17))))
                .containsExactlyInAnyOrder("minAge", "maxAge");
        }
    }

    @Nested
    @DisplayName("campaña")
    class Campaign {

        private UpdateCampaignRequestDTO campaign(int min, int max) {
            UpdateCampaignRequestDTO dto = new UpdateCampaignRequestDTO();
            UpdateCampaignRequestDTO.TargetAudienceDTO audience = dto.new TargetAudienceDTO();
            audience.setMinAge(min);
            audience.setMaxAge(max);
            dto.setTargetAudience(audience);
            return dto;
        }

        @Test
        @DisplayName("18 a 100 pasa")
        void adultRangePasses() {
            assertThat(invalidFields(validator.validate(campaign(18, 100)))).isEmpty();
        }

        @Test
        @DisplayName("17 se rechaza, validando la campaña y no solo el bloque de audiencia")
        void underageIsRejected() {
            // Se valida el DTO de afuera a propósito: sin @Valid en el campo, las
            // restricciones de la clase interna no corrían y cualquier edad pasaba.
            assertThat(invalidFields(validator.validate(campaign(13, 17))))
                .containsExactlyInAnyOrder("targetAudience.minAge", "targetAudience.maxAge");
        }

        @Test
        @DisplayName("más de 100 tampoco")
        void overHundredIsRejected() {
            assertThat(invalidFields(validator.validate(campaign(18, 101))))
                .containsExactly("targetAudience.maxAge");
        }
    }
}
