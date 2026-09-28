package com.verygana2.utils.games;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.verygana2.utils.games.GameBriefCatalog.BriefField;

/**
 * Numera los elementos del contenido que el juego identifica por un id.
 *
 * Las preguntas de la trivia llevan un {@code id} que el build lee
 * ({@code QuestionData.Id}), pero es contabilidad interna del juego: pedírselo al
 * anunciante era hacerle llevar un contador a mano, con dos formas seguras de
 * arruinarlo —repetir un número al insertar una pregunta en el medio, o saltarse
 * uno al borrar—. Y el esquema lo exige, así que un id repetido pasaba la validación
 * y el problema aparecía adentro del juego.
 *
 * Lo pone el backend por posición, igual que {@link GameConfigStamper} sella
 * {@code meta}: el orden de la lista es el que el anunciante ve en pantalla.
 */
@Component
public class GameBriefNumbering {

    /** Devuelve una copia del contenido con los ids escritos donde el catálogo los pide. */
    public Map<String, Object> apply(List<BriefField> fields, Map<String, Object> brief) {
        if (brief == null || brief.isEmpty()) return brief;

        Map<String, Object> result = new LinkedHashMap<>(brief);

        for (BriefField field : fields) {
            if (field.autoNumberKey() == null) continue;
            number(result, field.path().split("\\."), 0, field.autoNumberKey());
        }
        return result;
    }

    /** Baja por la ruta copiando cada tramo: el mapa que llega puede ser inmutable. */
    @SuppressWarnings("unchecked")
    private void number(Map<String, Object> node, String[] path, int depth, String key) {
        String segment = path[depth];
        Object child = node.get(segment);

        if (depth == path.length - 1) {
            if (!(child instanceof List<?> list)) return;

            List<Object> numbered = new ArrayList<>(list.size());
            int position = 1;
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
                    copy.put(key, position++);
                    numbered.add(copy);
                } else {
                    numbered.add(item);
                }
            }
            node.put(segment, numbered);
            return;
        }

        if (!(child instanceof Map<?, ?> map)) return;
        Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
        node.put(segment, copy);
        number(copy, path, depth + 1, key);
    }
}
