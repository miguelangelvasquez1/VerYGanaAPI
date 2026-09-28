package com.verygana2.utils.games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.verygana2.utils.games.GameBriefCatalog.BriefField;

/**
 * Recorta el esquema completo de un juego al subconjunto que llena el anunciante.
 *
 * El formulario del anunciante se pinta con el mismo par {@code json_schema} +
 * {@code ui_schema} que ya usa el del diseñador, solo que podado. Así no hay un
 * segundo formulario que mantener ni una lista de campos escrita en el frontend
 * que se desincronice del esquema real.
 *
 * El recorte también es la validación: el {@code minItems} del catálogo se escribe
 * sobre el del esquema original, de modo que «trivia necesita 10 preguntas» vive en
 * un solo lugar y lo hace cumplir el mismo motor que valida la entrega del
 * diseñador.
 */
@Component
public class BriefSchemaExtractor {

    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String TYPE = "type";
    private static final String UI_ORDER = "ui:order";

    /**
     * Esquema reducido, listo para {@code SchemaValidator}.
     *
     * Devuelve un esquema que exige exactamente las ramas del catálogo: cada tramo
     * intermedio queda como objeto requerido, y la hoja conserva su definición
     * original (tipos, {@code maxLength}, {@code required} de los items) con el
     * {@code minItems} del catálogo encima.
     */
    public Map<String, Object> jsonSchema(Map<String, Object> fullSchema, List<BriefField> fields) {
        Map<String, Object> root = objectNode();

        for (BriefField field : fields) {
            String[] path = field.path().split("\\.");
            List<Map<String, Object>> chain = resolve(fullSchema, path, true);
            // La ruta se resuelve entera antes de tocar el resultado: si el esquema no
            // la declara, no queremos dejar a medias un bloque requerido y vacío, que
            // rechazaría cualquier contenido sin decir por qué.
            if (chain == null) continue;

            Map<String, Object> targetParent = root;
            for (int i = 0; i < path.length; i++) {
                String key = path[i];
                addRequired(targetParent, key);

                if (i == path.length - 1) {
                    Map<String, Object> leaf = new LinkedHashMap<>(chain.get(i));
                    leaf.put("minItems", field.minItems());
                    leaf.put("maxItems", stricterMax(leaf.get("maxItems"), field.maxItems()));
                    hideAutoNumbered(leaf, field.autoNumberKey());
                    properties(targetParent).put(key, leaf);
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) properties(targetParent).get(key);
                    Map<String, Object> branch = existing != null ? existing : objectNode();
                    properties(targetParent).put(key, branch);
                    targetParent = branch;
                }
            }
        }

        return root;
    }

    /**
     * El {@code ui_schema} equivalente: mismas ramas, más los títulos y ayudas del
     * padre, que son los que le explican al anunciante qué está llenando.
     */
    public Map<String, Object> uiSchema(Map<String, Object> fullUiSchema, List<BriefField> fields) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (fullUiSchema == null) return root;

        // Solo se les reescribe el ui:order a los nodos que armamos acá; las hojas
        // vienen del ui_schema del juego y se copian para no mutarlo.
        Set<Map<String, Object>> built = Collections.newSetFromMap(new IdentityHashMap<>());
        built.add(root);

        for (BriefField field : fields) {
            String[] path = field.path().split("\\.");
            List<Map<String, Object>> chain = resolve(fullUiSchema, path, false);
            if (chain == null) continue;

            Map<String, Object> target = root;
            for (int i = 0; i < path.length; i++) {
                String key = path[i];
                Map<String, Object> sourceNode = chain.get(i);

                if (i == path.length - 1) {
                    target.put(key, uiLeaf(sourceNode, field.autoNumberKey()));
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) target.get(key);
                    Map<String, Object> branch = existing != null ? existing : new LinkedHashMap<>();
                    // Los ui:title / ui:description del bloque, sin arrastrar sus hermanos.
                    sourceNode.forEach((k, v) -> {
                        if (k.startsWith("ui:") && !UI_ORDER.equals(k)) branch.putIfAbsent(k, v);
                    });
                    built.add(branch);
                    target.put(key, branch);
                    target = branch;
                }
            }
        }

        built.forEach(BriefSchemaExtractor::stampOrder);
        return root;
    }

    /**
     * La hoja del {@code ui_schema}, sin el campo que pone el backend.
     *
     * Hay que sacarlo también del {@code ui:order} de los items: RJSF falla si el
     * orden nombra una propiedad que el esquema ya no declara, y el formulario del
     * anunciante no se dibujaría.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> uiLeaf(Map<String, Object> source, String autoNumberKey) {
        Map<String, Object> leaf = new LinkedHashMap<>(source);
        if (autoNumberKey == null) return leaf;

        leaf.remove(autoNumberKey);

        if (leaf.get("items") instanceof Map<?, ?> rawItems) {
            Map<String, Object> items = new LinkedHashMap<>((Map<String, Object>) rawItems);
            items.remove(autoNumberKey);
            if (items.get(UI_ORDER) instanceof List<?> order) {
                items.put(UI_ORDER, order.stream().filter(k -> !autoNumberKey.equals(k)).toList());
            }
            leaf.put("items", items);
        }
        return leaf;
    }

    /**
     * Los nodos de origen de cada tramo de la ruta, o {@code null} si alguno falta.
     *
     * @param throughProperties true para el json schema, donde cada tramo baja por
     *                          {@code properties}; false para el ui schema, que es plano
     */
    private static List<Map<String, Object>> resolve(
            Map<String, Object> root, String[] path, boolean throughProperties) {

        List<Map<String, Object>> chain = new ArrayList<>(path.length);
        Map<String, Object> current = root;

        for (String key : path) {
            // Lectura pura: properties() crea el nodo si falta, y acá estamos leyendo
            // el esquema de la entidad, que no se debe tocar.
            Object child = (throughProperties ? readProperties(current) : current).get(key);
            if (!(child instanceof Map<?, ?>)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> childMap = (Map<String, Object>) child;
            chain.add(childMap);
            current = childMap;
        }
        return chain;
    }

    /**
     * Saca del formulario el identificador que pone el backend.
     *
     * Sale de {@code properties} y de {@code required}: si quedara requerido, el
     * anunciante tendría que llenar un campo que ya no ve y el formulario sería
     * imposible de enviar.
     */
    @SuppressWarnings("unchecked")
    private static void hideAutoNumbered(Map<String, Object> leaf, String key) {
        if (key == null) return;
        if (!(leaf.get("items") instanceof Map<?, ?> rawItems)) return;

        Map<String, Object> items = new LinkedHashMap<>((Map<String, Object>) rawItems);

        if (items.get(PROPERTIES) instanceof Map<?, ?> props) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) props);
            copy.remove(key);
            items.put(PROPERTIES, copy);
        }
        if (items.get(REQUIRED) instanceof List<?> required) {
            items.put(REQUIRED, required.stream().filter(k -> !key.equals(k)).toList());
        }
        leaf.put("items", items);
    }

    /**
     * Raíz y tramos intermedios del brief. Cerrados: sin esto el anunciante puede
     * colgar cualquier clave al lado de las preguntas, y se guarda y se siembra en el
     * borrador del diseñador sin que nadie la valide.
     */
    private static Map<String, Object> objectNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(TYPE, "object");
        node.put(REQUIRED, new ArrayList<String>());
        node.put(PROPERTIES, new LinkedHashMap<String, Object>());
        node.put("additionalProperties", false);
        return node;
    }

    /** Si el esquema del juego ya trae un tope más bajo, manda ese. */
    private static int stricterMax(Object schemaMax, int catalogMax) {
        return schemaMax instanceof Number n ? Math.min(n.intValue(), catalogMax) : catalogMax;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readProperties(Map<String, Object> node) {
        Object props = node.get(PROPERTIES);
        return props instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Variante de escritura: solo se usa sobre los nodos que arma este extractor. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> node) {
        Object props = node.get(PROPERTIES);
        if (props instanceof Map<?, ?> m) return (Map<String, Object>) m;
        Map<String, Object> created = new LinkedHashMap<>();
        node.put(PROPERTIES, created);
        return created;
    }

    /** Sin duplicar: dos campos del mismo juego comparten el tramo {@code game}. */
    @SuppressWarnings("unchecked")
    private static void addRequired(Map<String, Object> node, String key) {
        Object req = node.get(REQUIRED);
        List<String> list = req instanceof List<?> l ? (List<String>) l : new ArrayList<>();
        if (!list.contains(key)) list.add(key);
        node.put(REQUIRED, list);
    }

    /**
     * Reescribe el {@code ui:order} con las claves que sobrevivieron al recorte. El
     * del esquema original nombra bloques que acá ya no existen, y un orden que
     * apunta a campos ausentes deja huecos en el formulario.
     */
    private static void stampOrder(Map<String, Object> node) {
        List<String> keys = node.keySet().stream().filter(k -> !k.startsWith("ui:")).toList();
        if (!keys.isEmpty()) node.put(UI_ORDER, new ArrayList<>(keys));
    }
}
