package com.verygana2.utils.games;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.verygana2.models.games.Game;
import com.verygana2.utils.games.GameBriefCatalog.BriefField;
import com.verygana2.utils.validators.games.GameConfigValidator;
import com.verygana2.utils.validators.games.SchemaValidator;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida el contenido que entrega el anunciante contra el esquema recortado del juego.
 *
 * Es el mismo motor que valida la entrega del diseñador
 * ({@link GameConfigValidator}), sobre un esquema más chico. Tiene que ser el mismo:
 * si el anunciante pasa una validación distinta a la que después corre en la entrega,
 * el diseñador recibe contenido que no puede entregar y no tiene cómo arreglarlo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameBriefValidator {

    private final SchemaValidator schemaValidator;
    private final GameConfigValidator gameConfigValidator;
    private final GameBriefCatalog catalog;
    private final BriefSchemaExtractor extractor;

    /** El esquema del formulario del anunciante, o {@code null} si el juego no pide contenido. */
    public Map<String, Object> briefJsonSchema(Game game) {
        List<BriefField> fields = catalog.fieldsFor(game);
        if (fields.isEmpty()) return null;
        return extractor.jsonSchema(gameConfigValidator.latestDefinition(game).getJsonSchema(), fields);
    }

    public Map<String, Object> briefUiSchema(Game game) {
        List<BriefField> fields = catalog.fieldsFor(game);
        if (fields.isEmpty()) return null;
        return extractor.uiSchema(gameConfigValidator.latestDefinition(game).getUiSchema(), fields);
    }

    /**
     * @throws ValidationException si el juego pide contenido y el que llegó no alcanza
     */
    public void validateOrThrow(Game game, Map<String, Object> brief) {
        List<BriefField> fields = catalog.fieldsFor(game);
        if (fields.isEmpty()) return;

        if (brief == null || brief.isEmpty()) {
            throw new ValidationException(missingContentMessage(game, fields));
        }

        List<ValidationError> errors = schemaValidator.validate(brief, briefJsonSchema(game));
        if (errors.isEmpty()) return;

        log.warn("Brief inválido para el juego {}: {} errores", game.getId(), errors.size());
        throw new ValidationException(
            "El contenido para " + game.getTitle() + " no está completo: " + GameConfigValidator.describe(errors));
    }

    /**
     * El mensaje que ve el anunciante cuando no mandó nada. Nombra las cantidades
     * porque «falta el contenido» no le dice qué hacer: necesita saber que son 10
     * preguntas y no una.
     */
    private String missingContentMessage(Game game, List<BriefField> fields) {
        StringBuilder sb = new StringBuilder("Para ")
            .append(game.getTitle())
            .append(" hay que enviar el contenido de la marca: ");

        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("al menos ").append(fields.get(i).minItems()).append(" ").append(fields.get(i).label());
        }
        return sb.toString();
    }
}
