package com.verygana2.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.WompiConfig;
import com.verygana2.dtos.wompi.WompiDepositRequest;
import com.verygana2.services.interfaces.WompiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class WompiServiceImpl implements WompiService {

    private final WebClient wompiWebClient;
    private final WompiConfig wompiConfig;
    private final ObjectMapper objectMapper;

    /**
     * Crear transacción con tarjeta
     */
    @Override
    public JsonNode createCardTransaction(WompiDepositRequest request, String reference, Long amountInCents) {
        log.info("🔵 Creando transacción con tarjeta. Referencia: {}", reference);

        Map<String, Object> payload = buildBasePayload(request, reference, amountInCents);

        // Método de pago
        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("type", "CARD");
        paymentMethod.put("token", request.getCardToken());
        paymentMethod.put("installments", request.getInstallments() != null ? request.getInstallments() : 1);
        payload.put("payment_method", paymentMethod);

        return callWompiApi("/transactions", payload);
    }

    /**
     * Crear transacción con Nequi
     */
    @Override
    public JsonNode createNequiTransaction(WompiDepositRequest request, String reference, Long amountInCents) {

        log.info("🔵 Creando transacción con Nequi. Referencia: {}", reference);

        Map<String, Object> payload = buildBasePayload(request, reference, amountInCents);

        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("type", "NEQUI");
        paymentMethod.put("phone_number", request.getNequiPhone());
        payload.put("payment_method", paymentMethod);

        return callWompiApi("/transactions", payload);
    }

    /**
     * Crear transacción con PSE
     */
    @Override
    public JsonNode createPSETransaction(WompiDepositRequest request, String reference, Long amountInCents) {
        log.info("🔵 Creando transacción con PSE. Referencia: {}", reference);

        Map<String, Object> payload = buildBasePayload(request, reference, amountInCents);

        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("type", "PSE");
        paymentMethod.put("user_type", request.getUserType());
        paymentMethod.put("user_legal_id_type", request.getIdType());
        paymentMethod.put("user_legal_id", request.getIdNumber());
        paymentMethod.put("financial_institution_code", request.getBankCode());
        paymentMethod.put("payment_description", "Recarga VerYGana");
        payload.put("payment_method", paymentMethod);

        return callWompiApi("/transactions", payload);
    }

    @Override
    public JsonNode getTransactionState(String transactionId) {
        log.info("🔍 Consultando estado de transacción: {}", transactionId);

        try {
            String response = wompiWebClient.get()
                    .uri("/transactions/" + transactionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wompiConfig.getPublicKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readTree(response);

        } catch (WebClientResponseException e) {
            log.error("❌ Error consultando transacción: {}", e.getMessage());
            throw new RuntimeException("Error al consultar Wompi: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error inesperado: {}", e.getMessage());
            throw new RuntimeException("Error procesando respuesta de Wompi", e);
        }
    }

    @Override
    public JsonNode getPSEBanks() {
        log.info("🏦 Obteniendo lista de bancos PSE");

        try {
            String response = wompiWebClient.get()
                    .uri("/pse/financial_institutions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wompiConfig.getPublicKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readTree(response);

        } catch (Exception e) {
            log.error("❌ Error obteniendo bancos PSE: {}", e.getMessage());
            throw new RuntimeException("Error al obtener bancos", e);
        }
    }

    @Override
    public boolean validateWebhookSignature(String payload, String signature, String timestamp, String sentAt) {
        try {
            String eventString = timestamp + "." + sentAt + "." + payload;

            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    wompiConfig.getEventSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            hmac.init(secretKey);

            byte[] hash = hmac.doFinal(eventString.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);

            boolean isValid = MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));

            log.info("🔐 Validación de firma: {}", isValid ? "✅ VÁLIDA" : "❌ INVÁLIDA");
            return isValid;

        } catch (Exception e) {
            log.error("❌ Error validando firma: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildBasePayload(
            WompiDepositRequest request,
            String reference,
            Long amountInCents) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount_in_cents", amountInCents);
        payload.put("currency", wompiConfig.getCurrency());
        payload.put("customer_email", request.getEmail());
        payload.put("reference", reference);

        // Customer data
        Map<String, String> customerData = new HashMap<>();
        customerData.put("phone_number", request.getPhone());
        customerData.put("full_name", request.getFullName());
        payload.put("customer_data", customerData);

        return payload;
    }

    private JsonNode callWompiApi(String endpoint, Map<String, Object> payload) {
        try {
            String response = wompiWebClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wompiConfig.getPrivateKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("❌ Error 4xx de Wompi: {}", body);
                                return Mono.error(new RuntimeException("Error de cliente Wompi: " + body));
                            }))
                    .onStatus(
                            status -> status.is5xxServerError(),
                            serverResponse -> {
                                log.error("❌ Error 5xx de Wompi");
                                return Mono.error(new RuntimeException("Error del servidor de Wompi"));
                            })
                    .bodyToMono(String.class)
                    .block();

            log.info("✅ Respuesta exitosa de Wompi");
            return objectMapper.readTree(response);

        } catch (WebClientResponseException e) {
            log.error("❌ Error en llamada a Wompi: Status={}, Body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error llamando a Wompi: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error inesperado llamando a Wompi: {}", e.getMessage());
            throw new RuntimeException("Error procesando llamada a Wompi", e);
        }
    }

    @Override
    public String getPublicKey() {
        return wompiConfig.getPublicKey();
    }
}
