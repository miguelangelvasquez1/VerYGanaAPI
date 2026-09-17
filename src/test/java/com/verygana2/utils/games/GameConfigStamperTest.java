package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los bloques {@code meta} y {@code personalization} los sella el backend.
 *
 * {@code meta} lo escribía el diseñador a mano y quedaba basura: en una solicitud
 * real de dash-runner terminó como {@code "Brand-id"} y {@code "brand id"},
 * inservible para cruzar telemetría; y en cali {@code brand_id} exige
 * {@code minLength: 1}, así que un valor vacío rompía la validación.
 *
 * {@code personalization} es el icono de moneda/llave, idéntico en todas las
 * campañas: pedirlo en cada brandeo era trabajo repetido y una vía más para que la
 * campaña saliera con el placeholder que traen los esquemas por defecto.
 */
class GameConfigStamperTest {

    private static final String COIN = "https://cdn.verygana.com/public/logos/moneda-llave.png";

    private final GameConfigStamper stamper = new GameConfigStamper(COIN);

    /** Como los esquemas de cali: declaran personalization con los dos iconos. */
    private static Map<String, Object> caliSchema() {
        return Map.of("properties", Map.of(
            "personalization", Map.of("properties", Map.of(
                "coin_url", Map.of("type", "string"),
                "coin_count_url", Map.of("type", "string")))));
    }

    /** Como los de bogotá: no existe el bloque. */
    private static Map<String, Object> bogotaSchema() {
        return Map.of("properties", Map.of("game", Map.of("properties", Map.of())));
    }

    @Nested
    @DisplayName("brandId")
    class BrandId {

        @Test
        @DisplayName("convierte el nombre de marca en un identificador estable")
        void slugifies() {
            assertThat(GameConfigStamper.brandId("Coca Cola", 2L)).isEqualTo("coca-cola");
        }

        @Test
        @DisplayName("quita tildes y eñes, que romperían la telemetría")
        void stripsAccents() {
            assertThat(GameConfigStamper.brandId("Piñata María", 2L)).isEqualTo("pinata-maria");
        }

        @Test
        @DisplayName("nunca devuelve vacío: cali exige minLength 1")
        void neverBlank() {
            assertThat(GameConfigStamper.brandId("", 7L)).isEqualTo("marca-7");
            assertThat(GameConfigStamper.brandId(null, 7L)).isEqualTo("marca-7");
            assertThat(GameConfigStamper.brandId("!!! ???", 7L)).isEqualTo("marca-7");
        }

        @Test
        @DisplayName("respeta el maxLength más restrictivo de los 20 esquemas")
        void respectsMaxLength() {
            assertThat(GameConfigStamper.brandId("a".repeat(120), 1L)).hasSize(50);
        }
    }

    @Nested
    @DisplayName("meta")
    class Meta {

        @Test
        @DisplayName("se escribe aunque la configuración no traiga el bloque")
        @SuppressWarnings("unchecked")
        void createsMeta() {
            Map<String, Object> out = stamper.stamp(Map.of("game", Map.of()), null, "coca-cola", "42");

            assertThat((Map<String, Object>) out.get("meta"))
                .containsEntry("brand_id", "coca-cola")
                .containsEntry("campaign_id", "42");
        }

        @Test
        @DisplayName("pisa lo que haya escrito el diseñador")
        @SuppressWarnings("unchecked")
        void overwritesDesignerInput() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("meta", Map.of("brand_id", "Brand-id", "campaign_id", "brand id"));

            Map<String, Object> out = stamper.stamp(config, null, "coca-cola", "42");

            assertThat((Map<String, Object>) out.get("meta"))
                .containsEntry("brand_id", "coca-cola")
                .containsEntry("campaign_id", "42");
        }

        @Test
        @DisplayName("un null conserva lo ya sellado, para que la lectura no pise la entrega")
        @SuppressWarnings("unchecked")
        void nullKeepsExisting() {
            Map<String, Object> config = Map.of("meta", Map.of("brand_id", "coca-cola", "campaign_id", ""));

            Map<String, Object> out = stamper.stamp(config, null, null, "42");

            assertThat((Map<String, Object>) out.get("meta"))
                .containsEntry("brand_id", "coca-cola")
                .containsEntry("campaign_id", "42");
        }
    }

    @Nested
    @DisplayName("personalization")
    class Personalization {

        @Test
        @DisplayName("los juegos de cali reciben el mismo icono en los dos campos")
        @SuppressWarnings("unchecked")
        void stampsCoinIcons() {
            Map<String, Object> out = stamper.stamp(Map.of(), caliSchema(), "x", "1");

            assertThat((Map<String, Object>) out.get("personalization"))
                .containsEntry("coin_url", COIN)
                .containsEntry("coin_count_url", COIN);
        }

        @Test
        @DisplayName("pisa el placeholder de placehold.co que traen los esquemas")
        @SuppressWarnings("unchecked")
        void overwritesPlaceholder() {
            Map<String, Object> config = Map.of("personalization", Map.of(
                "coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN",
                "coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT"));

            Map<String, Object> out = stamper.stamp(config, caliSchema(), "x", "1");

            assertThat((Map<String, Object>) out.get("personalization"))
                .containsEntry("coin_url", COIN)
                .containsEntry("coin_count_url", COIN);
        }

        @Test
        @DisplayName("a bogotá no se le inventa el bloque: su esquema no lo declara")
        void skipsWhenSchemaDoesNotDeclareIt() {
            assertThat(stamper.stamp(Map.of("game", Map.of()), bogotaSchema(), "x", "1"))
                .doesNotContainKey("personalization");
        }

        @Test
        @DisplayName("sin esquema tampoco se inventa")
        void skipsWithoutSchema() {
            assertThat(stamper.stamp(Map.of(), null, "x", "1")).doesNotContainKey("personalization");
        }
    }

    @Test
    @DisplayName("no toca el resto de la configuración")
    void keepsTheRest() {
        Map<String, Object> out = stamper.stamp(
            Map.of("game", Map.of("ball_speed", 500), "audio", Map.of()), caliSchema(), "x", "1");

        assertThat(out).containsKeys("game", "audio", "meta", "personalization");
    }
}
