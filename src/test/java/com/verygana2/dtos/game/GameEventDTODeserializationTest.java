package com.verygana2.dtos.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.enums.MetricType;
import com.verygana2.utils.audit.AuditConfig;

/** Lectura del cuerpo real que mandan los builds a /games/metrics (spec 013, RN-11). */
class GameEventDTODeserializationTest {

    private static final TypeReference<GameEventDTO<List<GameMetricDTO>>> TYPE = new TypeReference<>() {};

    private GameEventDTO<List<GameMetricDTO>> readCapture(ObjectMapper mapper) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/games/stack-tower-metrics.json")) {
            return mapper.readValue(in, TYPE);
        }
    }

    private void assertStackTower(GameEventDTO<List<GameMetricDTO>> event) {
        assertThat(event.getCampaignId()).isEqualTo(14L);
        assertThat(event.getIsBrandedMode()).isTrue();
        assertThat(event.getPayload()).hasSize(7);
        GameMetricDTO performance = event.getPayload().get(6);
        assertThat(performance.getKey()).isEqualTo("performance");
        assertThat(performance.getType()).isEqualTo(MetricType.DOUBLE);
        assertThat(((Number) performance.getValue()).intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("lee la captura de stack-tower con el mapper que usa MVC en runtime")
    void readsStackTowerCaptureWithRuntimeMapper() throws Exception {
        assertStackTower(readCapture(new AuditConfig().auditObjectMapper()));
    }

    @Test
    @DisplayName("lee la captura de stack-tower con el mapper de Jackson2ObjectMapperBuilder")
    void readsStackTowerCaptureWithBuilderMapper() throws Exception {
        assertStackTower(readCapture(Jackson2ObjectMapperBuilder.json().build()));
    }

    @Test
    @DisplayName("un tipo de métrica desconocido se lee como nulo")
    void unknownMetricTypeReadsAsNull() throws Exception {
        String json = "{\"payload\":[{\"key\":\"x\",\"type\":\"FLOAT\",\"value\":1}]}";
        GameEventDTO<List<GameMetricDTO>> event =
                new AuditConfig().auditObjectMapper().readValue(json, TYPE);
        assertThat(event.getPayload()).hasSize(1);
        assertThat(event.getPayload().get(0).getType()).isNull();
    }

    @Test
    @DisplayName("technicalData acepta un objeto o un texto")
    void technicalDataAcceptsObjectOrString() throws Exception {
        ObjectMapper mapper = new AuditConfig().auditObjectMapper();
        GameEventDTO<List<GameMetricDTO>> asObject =
                mapper.readValue("{\"technicalData\":{\"errors\":1}}", TYPE);
        GameEventDTO<List<GameMetricDTO>> asString =
                mapper.readValue("{\"technicalData\":\"load_time=3\"}", TYPE);
        assertThat(asObject.getTechnicalData().isObject()).isTrue();
        assertThat(asString.getTechnicalData().asText()).isEqualTo("load_time=3");
    }

    @Test
    @DisplayName("toString no incluye session_token ni user_hash")
    void toStringOmitsCredentials() throws Exception {
        String text = readCapture(new AuditConfig().auditObjectMapper()).toString();
        assertThat(text).doesNotContain("token-stack-1").doesNotContain("hash-stack-1");
    }
}
