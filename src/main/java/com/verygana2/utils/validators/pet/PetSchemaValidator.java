package com.verygana2.utils.validators.pet;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.utils.validators.games.SchemaValidator;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Valida los payloads del módulo de mascota contra los JSON Schema de
 * {@code resources/schemas/}, antes de persistirlos.
 *
 * Nace de un caso real: se publicó un ítem sin {@code externalId} ni {@code price}.
 * Quedó guardado y con estado "en el juego", pero era invisible —el catálogo omite
 * los que no tienen externalId— e incobrable. El schema convierte eso en un error
 * explícito en vez de un registro roto que nadie nota.
 *
 * Reusa {@link SchemaValidator} (networknt), el mismo motor que valida la config de
 * los juegos brandeados.
 */
@Component
@RequiredArgsConstructor
public class PetSchemaValidator {

    public enum PetSchema {
        CATALOG_ITEM("schemas/pet-catalog-item.schema.json"),
        SCENE("schemas/pet-scene.schema.json");

        private final String path;

        PetSchema(String path) {
            this.path = path;
        }
    }

    private final SchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    private final Map<PetSchema, Map<String, Object>> schemas = new EnumMap<>(PetSchema.class);

    /**
     * Se cargan todos al arrancar: si un schema está mal formado conviene enterarse
     * ahí y no cuando alguien intenta publicar.
     */
    @SuppressWarnings("unchecked")
    @PostConstruct
    public void loadSchemas() throws IOException {
        for (PetSchema schema : PetSchema.values()) {
            try (InputStream in = new ClassPathResource(schema.path).getInputStream()) {
                schemas.put(schema, objectMapper.readValue(in, Map.class));
            }
        }
    }

    public List<ValidationError> validate(PetSchema schema, Map<String, Object> data) {
        return schemaValidator.validate(data, schemas.get(schema));
    }

    /** Junta los mensajes en una línea, para el texto de la excepción. */
    public static String describe(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::getMessage).reduce((a, b) -> a + "; " + b).orElse("");
    }
}
