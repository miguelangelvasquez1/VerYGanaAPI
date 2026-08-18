package com.verygana2.utils.validators.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.utils.validators.games.SchemaValidator;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PetSchemaValidator")
class PetSchemaValidatorTest {

    private final PetSchemaValidator validator = build();

    private static PetSchemaValidator build() {
        ObjectMapper mapper = new ObjectMapper();
        PetSchemaValidator v =
                new PetSchemaValidator(new SchemaValidator(mapper), mapper);
        try {
            v.loadSchemas();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el schema", e);
        }
        return v;
    }

    private static Map<String, Object> validDraft() {
        Map<String, Object> d = new HashMap<>();
        d.put("externalId", 1000);
        d.put("name", "Croquetas");
        d.put("price", 25);
        return d;
    }

    @Test
    @DisplayName("borrador completo: sin errores")
    void validDraft_passes() {
        assertThat(validator.validate(PetSchemaValidator.PetSchema.CATALOG_ITEM, validDraft())).isEmpty();
    }

    /** El caso real: se publicó un ítem sin externalId ni price y quedó invisible e incobrable. */
    @Test
    @DisplayName("sin externalId ni price: los reporta a ambos")
    void missingRequiredFields_reported() {
        Map<String, Object> d = new HashMap<>();
        d.put("name", "Croquetas");

        List<ValidationError> errors = validator.validate(PetSchemaValidator.PetSchema.CATALOG_ITEM, d);

        // ValidationError no implementa toString, así que se comparan los mensajes.
        assertThat(errors).extracting(ValidationError::getMessage)
                .anyMatch(m -> m.contains("externalId"))
                .anyMatch(m -> m.contains("price"));
    }

    @Test
    @DisplayName("externalId por debajo de 1000 se rechaza: pisaría los ítems del build")
    void externalIdBelowReservedRange_rejected() {
        Map<String, Object> d = validDraft();
        d.put("externalId", 8);

        assertThat(validator.validate(PetSchemaValidator.PetSchema.CATALOG_ITEM, d)).isNotEmpty();
    }

    @Test
    @DisplayName("precio cero o negativo se rechaza")
    void nonPositivePrice_rejected() {
        Map<String, Object> d = validDraft();
        d.put("price", 0);

        assertThat(validator.validate(PetSchemaValidator.PetSchema.CATALOG_ITEM, d)).isNotEmpty();
    }

    // ── Escenas ───────────────────────────────────────────────────────────

    private static Map<String, Object> validObject() {
        Map<String, Object> o = new HashMap<>();
        o.put("objectId", "background_main");
        o.put("type", "background");
        o.put("objectKey", "scenes/bg.png");
        o.put("x", 0);
        o.put("y", 0);
        o.put("width", 1920);
        o.put("height", 1080);
        return o;
    }

    private static Map<String, Object> validScene() {
        Map<String, Object> s = new HashMap<>();
        s.put("sceneId", 1);
        s.put("objects", List.of(validObject()));
        return s;
    }

    private List<ValidationError> validateScene(Map<String, Object> scene) {
        return validator.validate(PetSchemaValidator.PetSchema.SCENE, scene);
    }

    @Test
    @DisplayName("escena completa: sin errores")
    void validScene_passes() {
        assertThat(validateScene(validScene())).isEmpty();
    }

    /** Una escena sin objetos se descarga pero no renderiza nada: no hay forma de notarlo. */
    @Test
    @DisplayName("escena sin objetos se rechaza")
    void sceneWithoutObjects_rejected() {
        Map<String, Object> s = validScene();
        s.put("objects", List.of());

        assertThat(validateScene(s)).isNotEmpty();
    }

    @Test
    @DisplayName("objeto sin objectKey se rechaza: no habría asset que mostrar")
    void objectWithoutKey_rejected() {
        Map<String, Object> o = validObject();
        o.remove("objectKey");
        Map<String, Object> s = validScene();
        s.put("objects", List.of(o));

        assertThat(validateScene(s)).extracting(ValidationError::getMessage)
                .anyMatch(m -> m.contains("objectKey"));
    }

    @Test
    @DisplayName("ancho o alto en cero se rechaza: el objeto sería invisible")
    void zeroSize_rejected() {
        Map<String, Object> o = validObject();
        o.put("width", 0);
        Map<String, Object> s = validScene();
        s.put("objects", List.of(o));

        assertThat(validateScene(s)).isNotEmpty();
    }

    @Test
    @DisplayName("scaleMultiplier es opcional pero no puede ser cero")
    void zeroScale_rejected() {
        Map<String, Object> o = validObject();
        o.put("scaleMultiplier", 0.0);
        Map<String, Object> s = validScene();
        s.put("objects", List.of(o));

        assertThat(validateScene(s)).isNotEmpty();
    }
}
