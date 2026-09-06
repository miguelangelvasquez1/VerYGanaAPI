package com.verygana2.utils.validators.games;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida la configuración de un juego brandeado contra el {@code json_schema} de su
 * {@link GameConfigDefinition}, antes de que salga del diseñador.
 *
 * Nace de un caso real en dash-runner: el diseñador escribió {@code 10} en
 * {@code key_spawn_probability}, un campo que el schema acota a {@code [0.0, 1.0]}.
 * Se guardó en el borrador, viajó a la preview y llegó al build, donde las llaves
 * dejaron de aparecer — y con ellas la recompensa de la campaña. Nadie lo frenó
 * porque el schema solo se usaba para pintar el formulario, nunca para validar.
 *
 * Reusa {@link SchemaValidator} (networknt), el mismo motor del módulo de mascotas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameConfigValidator {

    private final SchemaValidator schemaValidator;

    /**
     * La definición vigente de un juego.
     *
     * Es el mismo criterio que usa el panel del diseñador para pintar el formulario.
     * Tiene que serlo: si el formulario se dibuja con un schema y la validación
     * corre contra otro, el diseñador ve errores que no puede corregir.
     */
    public GameConfigDefinition latestDefinition(Game game) {
        return game.getConfigDefinitions().stream()
            .max(Comparator.comparing(GameConfigDefinition::getVersion))
            .orElseThrow(() -> new ValidationException(
                "El juego " + game.getTitle() + " no tiene definición de configuración"));
    }

    /**
     * @param config la configuración YA aplanada (sin los envoltorios
     *               {@code {assetId, url}} que produce el widget de assets), porque
     *               el schema declara esos campos como string.
     * @throws ValidationException si no cumple el schema
     */
    public void validateOrThrow(Game game, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new ValidationException("La configuración del juego está vacía");
        }

        List<ValidationError> errors = schemaValidator.validate(
            config, latestDefinition(game).getJsonSchema());

        if (errors.isEmpty()) {
            return;
        }

        log.warn("Config inválida para el juego {}: {} errores", game.getId(), errors.size());
        throw new ValidationException(
            "La configuración del juego no es válida: " + describe(errors));
    }

    /** Junta los mensajes para el texto de la excepción, acotado para no inundar la UI. */
    static String describe(List<ValidationError> errors) {
        String detail = errors.stream()
            .limit(10)
            .map(ValidationError::getMessage)
            .collect(Collectors.joining("; "));

        return errors.size() > 10
            ? detail + "; y " + (errors.size() - 10) + " error(es) más"
            : detail;
    }
}
