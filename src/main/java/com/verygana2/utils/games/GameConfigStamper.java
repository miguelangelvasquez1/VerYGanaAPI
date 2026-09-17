package com.verygana2.utils.games;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sella los bloques de la configuración que decide el backend, no el diseñador.
 *
 * Son dos, por razones distintas:
 *
 * <ul>
 *   <li>{@code meta} — {@code brand_id} y {@code campaign_id} identifican marca y
 *   campaña para la telemetría del build. El backend ya los conoce, y pedírselos al
 *   diseñador solo producía basura: en una solicitud real de dash-runner quedaron
 *   como {@code "Brand-id"} y {@code "brand id"}. Además en los juegos de cali
 *   {@code brand_id} exige {@code minLength: 1}, así que dejarlo vacío rompía la
 *   validación.</li>
 *
 *   <li>{@code personalization} — el icono de moneda/llave es el mismo en todas las
 *   campañas. Pedirlo en cada brandeo era trabajo repetido para el diseñador y una
 *   oportunidad más de que la campaña saliera con el placeholder de
 *   {@code placehold.co} que traen los esquemas por defecto.</li>
 * </ul>
 */
@Component
public class GameConfigStamper {

    /** El más restrictivo de los 20 esquemas es {@code maxLength: 50}. */
    private static final int MAX_ID_LENGTH = 50;

    private static final String META = "meta";
    private static final String PERSONALIZATION = "personalization";

    private final String coinIconUrl;

    public GameConfigStamper(@Value("${games.coin-icon-url}") String coinIconUrl) {
        this.coinIconUrl = coinIconUrl;
    }

    /**
     * Devuelve una copia de {@code config} con los bloques del backend escritos.
     *
     * @param jsonSchema esquema del juego; se consulta para no inventar claves. Solo
     *                   los juegos de cali declaran {@code personalization}, y
     *                   escribírsela a los de bogotá sería config muerta. Puede ser
     *                   nulo, y entonces {@code personalization} no se toca.
     * @param brandId    {@code null} conserva el que ya estuviera sellado, para que
     *                   una lectura no pise lo escrito en la entrega.
     * @param campaignId igual que {@code brandId}.
     */
    public Map<String, Object> stamp(Map<String, Object> config,
                                     Map<String, Object> jsonSchema,
                                     String brandId,
                                     String campaignId) {

        Map<String, Object> result = new LinkedHashMap<>(config);

        Map<String, Object> meta = blockOf(result, META);
        if (brandId != null) meta.put("brand_id", brandId);
        if (campaignId != null) meta.put("campaign_id", campaignId);
        result.put(META, meta);

        if (declaresCoinIcons(jsonSchema)) {
            Map<String, Object> personalization = blockOf(result, PERSONALIZATION);
            personalization.put("coin_url", coinIconUrl);
            personalization.put("coin_count_url", coinIconUrl);
            result.put(PERSONALIZATION, personalization);
        }

        return result;
    }

    /**
     * Identificador estable y legible a partir del nombre de marca: minúsculas, sin
     * tildes y con guiones. Cae a {@code marca-<id>} cuando el nombre no deja nada
     * utilizable, porque cali no acepta cadena vacía.
     */
    public static String brandId(String brandName, Long commercialId) {
        String slug = brandName == null ? "" : Normalizer.normalize(brandName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");

        if (slug.isBlank()) {
            return "marca-" + commercialId;
        }
        return slug.length() > MAX_ID_LENGTH ? slug.substring(0, MAX_ID_LENGTH) : slug;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> blockOf(Map<String, Object> config, String name) {
        Object current = config.get(name);
        return current instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();
    }

    /** Solo cali declara el bloque; no queremos escribirle claves muertas a bogotá. */
    @SuppressWarnings("unchecked")
    private static boolean declaresCoinIcons(Map<String, Object> jsonSchema) {
        if (jsonSchema == null) return false;

        Object root = jsonSchema.get("properties");
        if (!(root instanceof Map<?, ?> rootProps)) return false;

        Object block = ((Map<String, Object>) rootProps).get(PERSONALIZATION);
        if (!(block instanceof Map<?, ?> blockMap)) return false;

        Object props = ((Map<String, Object>) blockMap).get("properties");
        return props instanceof Map<?, ?> p && ((Map<String, Object>) p).containsKey("coin_url");
    }
}
