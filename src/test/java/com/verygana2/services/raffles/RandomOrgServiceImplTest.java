package com.verygana2.services.raffles;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.verygana2.dtos.raffle.responses.RandomOrgDrawResult;
import com.verygana2.dtos.raffle.responses.RandomOrgError;
import com.verygana2.dtos.raffle.responses.RandomOrgRandom;
import com.verygana2.dtos.raffle.responses.RandomOrgResponseDTO;
import com.verygana2.dtos.raffle.responses.RandomOrgResult;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RandomOrgServiceImpl}: validación de parámetros antes de
 * llamar a la API externa, y el manejo de las 3 formas en que Random.org
 * puede fallar (error de negocio, respuesta vacía, error de red).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RandomOrgServiceImpl")
class RandomOrgServiceImplTest {

    @Mock private RestTemplate restTemplate;

    private RandomOrgServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RandomOrgServiceImpl(restTemplate);
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.random.org/json-rpc/4/invoke");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
    }

    @Nested
    @DisplayName("validación de parámetros")
    class ParameterValidation {

        @Test
        @DisplayName("min negativo: lanza IllegalArgumentException")
        void negativeMin_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.generateRandomIntegers(-1, 10, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("max no mayor que min: lanza IllegalArgumentException")
        void maxNotGreaterThanMin_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.generateRandomIntegers(5, 5, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("count no positivo: lanza IllegalArgumentException")
        void nonPositiveCount_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 10, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("count mayor al tamaño del rango: lanza IllegalArgumentException")
        void countExceedsRange_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 4, 10)) // rango [0,4] = 5 números, pide 10
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("count mayor a 10.000: lanza IllegalArgumentException")
        void countAboveHardLimit_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 20000, 10001))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("llamada a la API")
    class ApiCall {

        private RandomOrgResponseDTO successResponse() {
            RandomOrgRandom random = new RandomOrgRandom(List.of(2, 0), "2026-01-01T00:00:00Z", 12345L, "hash", "license");
            RandomOrgResult result = new RandomOrgResult(random, "signature", 100, 900, 50, java.util.Map.of());
            return new RandomOrgResponseDTO("2.0", result, null, "req-1");
        }

        @Test
        @DisplayName("respuesta exitosa: retorna los índices y la metadata de la firma")
        void success_returnsIndicesAndMetadata() {
            when(restTemplate.postForEntity(any(String.class), any(), org.mockito.ArgumentMatchers.eq(RandomOrgResponseDTO.class)))
                    .thenReturn(ResponseEntity.ok(successResponse()));

            RandomOrgDrawResult result = service.generateRandomIntegers(0, 2, 2);

            assertThat(result.indices()).containsExactly(2, 0);
            assertThat(result.metadata().getSerialNumber()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("Random.org retorna un error de negocio en el body: lanza RandomOrgException")
        void businessError_throwsRandomOrgException() {
            RandomOrgResponseDTO response = new RandomOrgResponseDTO("2.0", null,
                    new RandomOrgError(401, "Invalid API key"), "req-1");
            when(restTemplate.postForEntity(any(String.class), any(), org.mockito.ArgumentMatchers.eq(RandomOrgResponseDTO.class)))
                    .thenReturn(ResponseEntity.ok(response));

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 2, 1))
                    .isInstanceOf(RandomOrgException.class);
        }

        @Test
        @DisplayName("body vacío: lanza RandomOrgException")
        void emptyBody_throwsRandomOrgException() {
            when(restTemplate.postForEntity(any(String.class), any(), org.mockito.ArgumentMatchers.eq(RandomOrgResponseDTO.class)))
                    .thenReturn(ResponseEntity.ok(null));

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 2, 1))
                    .isInstanceOf(RandomOrgException.class);
        }

        @Test
        @DisplayName("falla de red/conexión: lanza RandomOrgException")
        void networkFailure_throwsRandomOrgException() {
            when(restTemplate.postForEntity(any(String.class), any(), org.mockito.ArgumentMatchers.eq(RandomOrgResponseDTO.class)))
                    .thenThrow(new RestClientException("timeout"));

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 2, 1))
                    .isInstanceOf(RandomOrgException.class);
        }
    }
}
