package com.verygana2.utils.validators.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;

import jakarta.validation.ValidationException;

/**
 * La configuración de un brandeo se valida contra el schema del juego antes de
 * entregarse al anunciante.
 *
 * El caso que motiva esto es real: en dash-runner se entregó un diseño con
 * {@code key_spawn_probability = 10} sobre un campo acotado a {@code [0.0, 1.0]}.
 * Las llaves dejaron de aparecer, y como la recompensa de esa campaña se paga por
 * llave recogida, la campaña pagaba cero. El schema existía y describía el límite;
 * simplemente nadie lo evaluaba.
 *
 * El schema se lee del seed real para que el test siga al esquema y no a una copia.
 */
class GameConfigValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> dashRunnerSchema;
    private static GameConfigValidator validator;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUp() throws IOException {
        dashRunnerSchema = MAPPER.readValue(
            firstJsonBlob("db/seed/games/bogota/dash-runner.sql"), Map.class);
        validator = new GameConfigValidator(new SchemaValidator(MAPPER));
    }

    /** El json_schema es el primer literal entrecomillado del INSERT del seed. */
    private static String firstJsonBlob(String classpathResource) throws IOException {
        String sql = new String(
            new ClassPathResource(classpathResource).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("'(\\{\\s*\\n.*?\\n\\})',", Pattern.DOTALL).matcher(sql);
        if (!m.find()) {
            throw new IllegalStateException("No se encontró el json_schema en " + classpathResource);
        }
        return m.group(1);
    }

    private static Game gameWithSchema(Map<String, Object> schema) {
        return Game.builder()
            .id(11L)
            .title("Dash Runner")
            .configDefinitions(List.of(GameConfigDefinition.builder()
                .version(1L)
                .jsonSchema(schema)
                .build()))
            .build();
    }

    /** Una configuración que cumple el schema de dash-runner. */
    private static Map<String, Object> validConfig() {
        return Map.of(
            "meta", Map.of("brand_id", "coca-cola", "campaign_id", "verano-2026"),
            "branding", Map.of("main_logo_url", "https://cdn.verygana.com/public/logo.png"),
            "game_config", Map.of(),
            "game", Map.of(
                "background_phrases", List.of("Corre y gana"),
                "character_image_url", "https://cdn.verygana.com/public/personaje.png",
                "character_color", "#e32400",
                "key_spawn_probability", 0.5),
            "audio", Map.of(
                "key_win_url", "https://cdn.verygana.com/public/llave.mp3",
                "lose_url", "https://cdn.verygana.com/public/derrota.mp3"),
            "texts", Map.of("game_over_messages", List.of("Intenta de nuevo")),
            "rewards", Map.of("keys_per_action", 10));
    }

    private static Map<String, Object> configWith(String section, String key, Object value) {
        Map<String, Object> config = new java.util.HashMap<>(validConfig());
        Map<String, Object> block = new java.util.HashMap<>(
            (Map<String, Object>) config.get(section));
        block.put(key, value);
        config.put(section, block);
        return config;
    }

    @Test
    @DisplayName("una configuración completa y dentro de rango pasa")
    void validConfigPasses() {
        assertThatCode(() -> validator.validateOrThrow(gameWithSchema(dashRunnerSchema), validConfig()))
            .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("rechaza lo que llegó al build en producción")
    class Regressions {

        @Test
        @DisplayName("key_spawn_probability fuera del rango [0,1] — el bug de dash-runner")
        void rejectsProbabilityOutOfRange() {
            assertThatThrownBy(() -> validator.validateOrThrow(
                    gameWithSchema(dashRunnerSchema), configWith("game", "key_spawn_probability", 10)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no es válida");
        }

        @Test
        @DisplayName("una configuración vacía no se puede entregar")
        void rejectsEmptyConfig() {
            assertThatThrownBy(() -> validator.validateOrThrow(gameWithSchema(dashRunnerSchema), Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("vacía");
        }

        @Test
        @DisplayName("falta un bloque obligatorio del schema")
        void rejectsMissingSection() {
            Map<String, Object> config = new java.util.HashMap<>(validConfig());
            config.remove("audio");

            assertThatThrownBy(() -> validator.validateOrThrow(gameWithSchema(dashRunnerSchema), config))
                .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("un asset sin subir queda como \"\" y no pasa el format: uri")
        void rejectsEmptyAssetUrl() {
            // Los esquemas de bogotá traen default "" en los campos de asset. Ese
            // string vacío llegaba al build, que intentaba descargarlo, recibía el
            // HTML de la página y fallaba con EncodingError.
            assertThatThrownBy(() -> validator.validateOrThrow(
                    gameWithSchema(dashRunnerSchema), configWith("audio", "key_win_url", "")))
                .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("una URL que no es URL tampoco pasa")
        void rejectsMalformedUrl() {
            assertThatThrownBy(() -> validator.validateOrThrow(
                    gameWithSchema(dashRunnerSchema), configWith("audio", "lose_url", "pendiente")))
                .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("un color fuera del largo permitido")
        void rejectsShortColor() {
            assertThatThrownBy(() -> validator.validateOrThrow(
                    gameWithSchema(dashRunnerSchema), configWith("game", "character_color", "#f")))
                .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    @DisplayName("el schema real declara el rango que se violó")
    @SuppressWarnings("unchecked")
    void schemaActuallyBoundsTheProbability() {
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>)
            ((Map<String, Object>) dashRunnerSchema.get("properties")).get("game")).get("properties");
        Map<String, Object> probability = (Map<String, Object>) props.get("key_spawn_probability");

        assertThat(probability.get("maximum")).isEqualTo(1.0);
    }
}
