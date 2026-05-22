package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.verygana2.dtos.raffle.responses.RandomOrgResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;

@ExtendWith(MockitoExtension.class)
@DisplayName("RandomOrgServiceImpl")
class RandomOrgServiceImplTest {

    @Mock RestTemplate restTemplate;

    @InjectMocks RandomOrgServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.random.org/json-rpc/4/invoke");
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
    }

    // ─── validateParameters ───────────────────────────────────────────────────

    @Nested
    @DisplayName("parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when min is negative")
        void throwsWhenMinNegative() {
            assertThatThrownBy(() -> service.generateRandomIntegers(-1, 10, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when max is less than min")
        void throwsWhenMaxLessThanMin() {
            assertThatThrownBy(() -> service.generateRandomIntegers(10, 5, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when count is zero")
        void throwsWhenCountZero() {
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 10, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when count is negative")
        void throwsWhenCountNegative() {
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 10, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when count exceeds range size")
        void throwsWhenCountExceedsRange() {
            // range [0, 4] has 5 values; requesting 6 is invalid
            assertThatThrownBy(() -> service.generateRandomIntegers(0, 4, 6))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unique numbers");
        }
    }

    // ─── generateRandomIntegers ───────────────────────────────────────────────

    @Nested
    @DisplayName("generateRandomIntegers")
    class GenerateRandomIntegers {

        @SuppressWarnings("unchecked")
        private void stubRestTemplate(RandomOrgResponseDTO body) {
            when(restTemplate.postForEntity(any(String.class), any(), eq(RandomOrgResponseDTO.class)))
                    .thenReturn(ResponseEntity.ok(body));
        }

        @Test
        @DisplayName("throws RandomOrgException when REST call fails")
        void throwsWhenRestCallFails() {
            when(restTemplate.postForEntity(any(String.class), any(), eq(RandomOrgResponseDTO.class)))
                    .thenThrow(new RestClientException("connection refused"));

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 100, 5))
                    .isInstanceOf(RandomOrgException.class);
        }

        @Test
        @DisplayName("throws RandomOrgException when response body is null")
        void throwsWhenBodyNull() {
            when(restTemplate.postForEntity(any(String.class), any(), eq(RandomOrgResponseDTO.class)))
                    .thenReturn(ResponseEntity.ok(null));

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 100, 5))
                    .isInstanceOf(RandomOrgException.class)
                    .hasMessageContaining("empty response");
        }

        @Test
        @DisplayName("throws RandomOrgException when API returns an error field")
        void throwsWhenApiReturnsError() {
            com.verygana2.dtos.raffle.responses.RandomOrgError error =
                    new com.verygana2.dtos.raffle.responses.RandomOrgError();
            error.setCode(400);
            error.setMessage("invalid key");

            RandomOrgResponseDTO body = new RandomOrgResponseDTO();
            body.setError(error);

            stubRestTemplate(body);

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 100, 5))
                    .isInstanceOf(RandomOrgException.class)
                    .hasMessageContaining("invalid key");
        }

        @Test
        @DisplayName("throws RandomOrgException when result or random data is null")
        void throwsWhenResultNull() {
            RandomOrgResponseDTO body = new RandomOrgResponseDTO();
            body.setResult(null);

            stubRestTemplate(body);

            assertThatThrownBy(() -> service.generateRandomIntegers(0, 100, 5))
                    .isInstanceOf(RandomOrgException.class)
                    .hasMessageContaining("invalid result");
        }

        @Test
        @DisplayName("returns list of random integers on successful response")
        void returnsRandomIntegers() {
            com.verygana2.dtos.raffle.responses.RandomOrgRandom randomData =
                    new com.verygana2.dtos.raffle.responses.RandomOrgRandom();
            randomData.setData(List.of(3, 7, 12, 45, 99));

            com.verygana2.dtos.raffle.responses.RandomOrgResult result =
                    new com.verygana2.dtos.raffle.responses.RandomOrgResult();
            result.setRandom(randomData);
            result.setBitsUsed(100);
            result.setBitsLeft(900);

            RandomOrgResponseDTO body = new RandomOrgResponseDTO();
            body.setResult(result);

            stubRestTemplate(body);

            List<Integer> integers = service.generateRandomIntegers(0, 100, 5);

            assertThat(integers).containsExactly(3, 7, 12, 45, 99);
        }
    }
}
