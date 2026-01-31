package com.verygana2.services.raffles;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.verygana2.dtos.raffle.requests.RandomOrgParams;
import com.verygana2.dtos.raffle.requests.RandomOrgRequestDTO;
import com.verygana2.dtos.raffle.responses.RandomOrgResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;
import com.verygana2.services.interfaces.raffles.RandomOrgService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RandomOrgServiceImpl implements RandomOrgService {

    private final RestTemplate restTemplate;

    @Value("${random-org.api-url}")
    private String apiUrl;

    @Value("${random-org.api-key}")
    private String apiKey;

    @Override
    @SuppressWarnings("null")
    public List<Integer> generateRandomIntegers(int min, int max, int count) {

        log.info("Requesting {} random integers from Random.org (range: {}-{})", count, min, max);

        // Validaciones
        validateParameters(min, max, count);

        // Construir request
        RandomOrgRequestDTO request = RandomOrgRequestDTO.builder().jsonrpc("2.0").method("generateIntegers")
                .params(RandomOrgParams.builder()
                        .apiKey(apiKey)
                        .n(count)
                        .min(min)
                        .max(max)
                        .replacement(false)
                        .build())
                .id(UUID.randomUUID().toString())
                .build();

        // Configurar headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RandomOrgRequestDTO> entity = new HttpEntity<>(request, headers);

        try {
            // Llamar a la API
            ResponseEntity<RandomOrgResponseDTO> response = restTemplate.postForEntity(apiUrl, entity,
                    RandomOrgResponseDTO.class);

            // Validar respuesta
            RandomOrgResponseDTO body = response.getBody();

            if (body == null) {
                throw new RandomOrgException("Random.org returned empty response");
            }

            if (body.getError() != null) {
                log.error("Random.org API error : {} - {}", body.getError().getCode(), body.getError().getMessage());
                throw new RandomOrgException("Random.org error :" + body.getError().getMessage());
            }

            if (body.getResult() == null || body.getResult().getRandom() == null) {
                throw new RandomOrgException("Random.org returned invalid result");
            }

            List<Integer> randomIndexes = body.getResult().getRandom().getData();

            log.info("Random.org response: {} indices generated. Serial: {}, Bits used: {}, Bits left: {}",
                    randomIndexes.size(),
                    body.getResult().getRandom().getSerialNumber(),
                    body.getResult().getBitsUsed(),
                    body.getResult().getBitsLeft());

            return randomIndexes;

        } catch (RestClientException e) {
            log.error("Failed to connect to Random.org", e);
            throw new RandomOrgException("Failed to generate random number from Random.org: " + e.getMessage() + e);
        }

    }

    /**
     * Valida los parámetros antes de llamar a la API
     */
    private void validateParameters(int min, int max, int count) {
        if (min < 0) {
            throw new IllegalArgumentException("Min value cannot be negative");
        }

        if (max < min) {
            throw new IllegalArgumentException("Max value must be greater than or equal to min");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        if (count > (max - min + 1)) {
            throw new IllegalArgumentException(
                    String.format("Cannot generate %d unique numbers from range [%d, %d]",
                            count, min, max));
        }

        // Random.org limit: max 10,000 numbers per request
        if (count > 10000) {
            throw new IllegalArgumentException(
                    "Random.org supports maximum 10,000 numbers per request");
        }
    }

}
