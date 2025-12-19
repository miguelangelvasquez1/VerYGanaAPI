package com.verygana2.utils.validators;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.models.enums.MetricType;
import com.verygana2.models.games.GameMetricDefinition;

@Component
public class MetricValidator {

    public void validateMetrics(
            List<GameMetricDTO> metrics,
            List<GameMetricDefinition> definitions
    ) {

        if (definitions == null || definitions.isEmpty()) {
            if (metrics != null && !metrics.isEmpty()) {
                throw new IllegalArgumentException("Game does not accept metrics");
            }
            return;
        }

        Map<String, GameMetricDefinition> definitionMap =
                definitions.stream()
                        .collect(Collectors.toMap(
                                GameMetricDefinition::getKey,
                                Function.identity()
                        ));

        Set<String> receivedKeys = new HashSet<>();

        if (metrics != null) {
            for (GameMetricDTO metric : metrics) {

                // 1. Clave duplicada
                if (!receivedKeys.add(metric.getKey())) {
                    throw new IllegalArgumentException(
                            "Duplicate metric key: " + metric.getKey()
                    );
                }

                // 2. Métrica no definida
                GameMetricDefinition definition =
                        definitionMap.get(metric.getKey());

                if (definition == null) {
                    throw new IllegalArgumentException(
                            "Metric not allowed: " + metric.getKey()
                    );
                }

                // 3. Tipo coincide
                MetricType expectedType =
                        MetricType.valueOf(definition.getType());

                if (metric.getType() != expectedType) {
                    throw new IllegalArgumentException(
                            "Invalid type for metric '" + metric.getKey()
                                    + "'. Expected " + expectedType
                                    + " but got " + metric.getType()
                    );
                }

                // 4. Valor compatible con el tipo
                validateValueType(metric);

                // 5. Valor requerido
                if (Boolean.TRUE.equals(definition.getRequired())
                        && metric.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Required metric missing value: " + metric.getKey()
                    );
                }
            }
        }

        // 6. Validar métricas requeridas faltantes
        for (GameMetricDefinition definition : definitions) {
            if (Boolean.TRUE.equals(definition.getRequired())
                    && !receivedKeys.contains(definition.getKey())) {
                throw new IllegalArgumentException(
                        "Missing required metric: " + definition.getKey()
                );
            }
        }
    }

    private void validateValueType(GameMetricDTO metric) {
        Object value = metric.getValue();

        if (value == null) {
            return;
        }

        switch (metric.getType()) {
            case MetricType.INT -> {
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException(
                            "Metric '" + metric.getKey()
                                    + "' must be INTEGER"
                    );
                }
            }
            case MetricType.DECIMAL -> {
                if (!(value instanceof Long)) {
                    throw new IllegalArgumentException(
                            "Metric '" + metric.getKey()
                                    + "' must be LONG"
                    );
                }
            }
            case MetricType.DOUBLE -> {
                if (!(value instanceof Double)) {
                    throw new IllegalArgumentException(
                            "Metric '" + metric.getKey()
                                    + "' must be DOUBLE"
                    );
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(
                            "Metric '" + metric.getKey()
                                    + "' must be BOOLEAN"
                    );
                }
            }
            case STRING -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "Metric '" + metric.getKey()
                                    + "' must be STRING"
                    );
                }
            }
            default -> throw new IllegalStateException(
                    "Unsupported metric type: " + metric.getType()
            );
        }
    }
}
