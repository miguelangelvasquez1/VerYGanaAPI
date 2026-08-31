package com.verygana2.dtos.user.commercial.onboarding;

import java.util.List;

import com.verygana2.models.enums.commercial.diagnostic.DiagnosticQuestionType;

/**
 * Catálogo del cuestionario de diagnóstico comercial que consume el front para
 * renderizar el paso 4 del onboarding.
 * {@code GET /commercials/onboarding/diagnostic/questionnaire}.
 *
 * Cada {@code fieldName} corresponde a un campo de {@code CommercialDiagnosticRequestDTO}
 * (el cuerpo del POST del diagnóstico); cada {@code value} de opción corresponde a
 * una constante del enum de ese campo (o "true"/"false" si el tipo es BOOLEAN).
 */
public record DiagnosticQuestionnaireResponseDTO(
        int version,
        String openingMessage,
        List<String> openingActions,
        List<Section> sections) {

    public record Section(
            String code,
            String title,
            String subtitle,
            List<Question> questions) {
    }

    public record Question(
            String code,
            String fieldName,
            String text,
            String helpText,
            DiagnosticQuestionType type,
            boolean required,
            Integer maxSelections,
            boolean ordered,
            /** null = siempre visible. */
            Dependency dependsOn,
            List<Option> options) {
    }

    public record Option(String value, String label, boolean exclusive) {
    }

    /** Mostrar la pregunta solo si la respuesta a {@code questionCode} está en {@code values}. */
    public record Dependency(String questionCode, List<String> values) {
    }
}
