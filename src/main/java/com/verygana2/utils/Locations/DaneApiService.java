package com.verygana2.utils.Locations;

import java.util.*;
import java.util.regex.*;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DaneApiService {

    private static final String API_URL = "https://www.datos.gov.co/resource/82di-kkh9.json?$limit=5000";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DaneApiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Devuelve la lista de JsonNode (cada nodo es un registro del dataset).
     */
    public List<JsonNode> fetchRawRecords() {
        try {
            String json = restTemplate.getForObject(API_URL, String.class);
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                root.forEach(list::add);
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error consumiendo API DANE: " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    /**
     * Extrae el código de municipio (5 dígitos) intentando varios campos y buscando
     * cualquier valor que coincida con \\d{5}.
     */
    public Optional<String> extractMunicipalityCode(JsonNode node) {
        // Campos comunes que podrían contener el código
        String[] candidateKeys = new String[] {
            "codigo_dane_del_municipio", "codigo_municipio", "c_digo_dane_del_municipio",
            "cod_dane_mpio", "codigo", "municipio_id", "cod_mpio", "código_dane", "codigo_dane"
        };

        Pattern fiveDigits = Pattern.compile("\\d{5}");

        for (String key : candidateKeys) {
            if (node.has(key)) {
                String v = node.path(key).asText("").trim();
                Matcher m = fiveDigits.matcher(v);
                if (m.find()) return Optional.of(m.group());
            }
        }

        // Si no encontramos por keys conocidas, buscaremos cualquier valor que sea 5 dígitos
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode fNode = node.get(name);
            String text = fNode == null ? "" : fNode.asText("");
            Matcher m = fiveDigits.matcher(text);
            if (m.find()) return Optional.of(m.group());
        }

        return Optional.empty();
    }

    /**
     * Extrae un nombre heurístico (municipio o departamento) buscando campos comunes.
     */
    public Optional<String> extractName(JsonNode node, String type) {
        // type == "municipio" or "departamento"
        String[] muniKeys = new String[] {"municipio", "nombre_municipio", "municipio_nombre", "nombre", "nom_mpio", "mpio"};
        String[] deptKeys = new String[] {"departamento", "nombre_departamento", "departamento_nombre", "depto"};

        String[] keys = type.equals("municipio") ? muniKeys : deptKeys;

        for (String key : keys) {
            if (node.has(key)) {
                String v = node.path(key).asText("").trim();
                if (!v.isEmpty()) return Optional.of(v);
            }
        }
        // fallback: cualquier campo de texto que no sea numérico
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode fNode = node.get(name);
            String val = fNode == null ? "" : fNode.asText("").trim();
            if (!val.isEmpty() && !val.matches("\\d+")) {
                return Optional.of(val);
            }
        }
        

        return Optional.empty();
    }
}
