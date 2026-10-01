package com.verygana2.utils.validators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.verygana2.config.TargetingProperties;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.dtos.survey.CreateSurveyRequest;
import com.verygana2.dtos.targeting.OptionalTargetAudienceDTO;

import jakarta.validation.ConstraintViolation;

@DisplayName("@MinTargetAge — edad mínima configurable (app.targeting.min-age)")
class MinTargetAgeValidatorTest {

    private AnnotationConfigApplicationContext context;
    private LocalValidatorFactoryBean validator;
    private TargetingProperties properties;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(TargetingProperties.class);
        context.refresh();
        properties = context.getBean(TargetingProperties.class);

        // Misma fábrica que usa Spring Boot: instancia los validadores como beans e inyecta TargetingProperties.
        validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(context);
        validator.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        validator.close();
        context.close();
    }

    private static OptionalTargetAudienceDTO audience(Integer minAge, Integer maxAge) {
        return OptionalTargetAudienceDTO.builder().minAge(minAge).maxAge(maxAge).build();
    }

    @Test
    @DisplayName("el valor por defecto es 18")
    void defaultIs18() {
        assertThat(properties.getMinAge()).isEqualTo(18);
    }

    @Test
    @DisplayName("17 se rechaza y 18 se acepta")
    void boundary() {
        assertThat(validator.validate(audience(17, 40))).hasSize(1);
        assertThat(validator.validate(audience(18, 40))).isEmpty();
    }

    @Test
    @DisplayName("la edad de 13 a 17, válida antes, ahora se rechaza en minAge y maxAge")
    void formerlyValidAgesAreRejected() {
        Set<ConstraintViolation<OptionalTargetAudienceDTO>> violations = validator.validate(audience(13, 17));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("minAge", "maxAge");
    }

    @Test
    @DisplayName("null es válido (campo opcional)")
    void nullIsValid() {
        assertThat(validator.validate(audience(null, null))).isEmpty();
    }

    @Test
    @DisplayName("el mensaje muestra la edad configurada")
    void messageShowsConfiguredAge() {
        Set<ConstraintViolation<OptionalTargetAudienceDTO>> violations = validator.validate(audience(17, null));

        assertThat(violations).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("Minimum age must be at least 18");
    }

    @Test
    @DisplayName("cambiar la propiedad cambia el mínimo sin tocar código")
    void followsConfiguration() {
        properties.setMinAge(21);

        assertThat(validator.validate(audience(20, 40))).hasSize(1);
        assertThat(validator.validate(audience(21, 40))).isEmpty();

        properties.setMinAge(16);

        assertThat(validator.validate(audience(16, 40))).isEmpty();
        assertThat(validator.validate(audience(15, 40)).iterator().next().getMessage())
                .isEqualTo("Minimum age must be at least 16");
    }

    @Test
    @DisplayName("mensaje por defecto cuando el campo no define uno (surveys)")
    void defaultMessage() {
        CreateSurveyRequest request = CreateSurveyRequest.builder().minAge(17).build();

        assertThat(validator.validateProperty(request, "minAge")).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("Age must be at least 18");
    }

    @Test
    @DisplayName("application.yml define app.targeting.min-age=18 y se enlaza a TargetingProperties")
    void bindsFromApplicationYml() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropertiesConfig.class)
                .run(ctx -> {
                    assertThat(ctx.getEnvironment().getProperty("app.targeting.min-age")).isEqualTo("18");
                    assertThat(ctx.getBean(TargetingProperties.class).getMinAge()).isEqualTo(18);
                });

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("app.targeting.min-age=21")
                .run(ctx -> assertThat(ctx.getBean(TargetingProperties.class).getMinAge()).isEqualTo(21));
    }

    @EnableConfigurationProperties(TargetingProperties.class)
    static class PropertiesConfig {
    }

    @Test
    @DisplayName("PUT /campaigns: la audiencia anidada también respeta el mínimo")
    void updateCampaignNestedAudience() {
        UpdateCampaignRequestDTO request = new UpdateCampaignRequestDTO();
        UpdateCampaignRequestDTO.TargetAudienceDTO audience = request.new TargetAudienceDTO();
        audience.setMinAge(16);
        audience.setMaxAge(30);
        request.setTargetAudience(audience);

        assertThat(validator.validate(request)).singleElement()
                .satisfies(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("targetAudience.minAge");
                    assertThat(v.getMessage()).isEqualTo("Minimum age must be at least 18");
                });

        audience.setMinAge(18);
        assertThat(validator.validate(request)).isEmpty();
    }
}
