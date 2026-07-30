package com.verygana2.controllers.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.dtos.zapsign.ZapSignCreateWebhookRequestDTO;
import com.verygana2.dtos.zapsign.ZapSignWebhookHeaderDTO;
import com.verygana2.dtos.zapsign.ZapSignWebhookResponseDTO;
import com.verygana2.dtos.zapsign.ZapSignWebhookSetupResultDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.zapsign.ZapSignApiException;
import com.verygana2.services.zapsign.ZapSignClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Setup de infraestructura (correr una sola vez por ambiente): registra en ZapSign los
 * webhooks que apuntan a /zapsign/events con el header secreto configurado. ZapSign solo
 * permite configurar ese header vía API (no desde su dashboard) — por eso este endpoint
 * en vez de instrucciones manuales para el equipo de ops.
 *
 * Registra dos webhooks porque no hay certeza en la documentación de ZapSign de que
 * "background_check_completed" sea válido junto con "doc_signed" en un solo registro;
 * si alguno de los dos falla (ej. type inválido), el otro igual se registra — la respuesta
 * indica el resultado de cada uno por separado.
 */
@Slf4j
@RestController
@RequestMapping("/admin/zapsign/webhooks")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class ZapSignWebhookAdminController {

    private static final String[] EVENT_TYPES = {"doc_signed", "background_check_completed"};

    private final ZapSignClient zapSignClient;
    private final ZapSignConfig zapSignConfig;

    @Value("${app.base-url}")
    private String backendBaseUrl;

    @PostMapping("/setup")
    public ResponseEntity<List<ZapSignWebhookSetupResultDTO>> setup() {
        String webhookSecret = zapSignConfig.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessException(
                    "ZAPSIGN_WEBHOOK_SECRET no está configurado — el webhook quedaría sin protección. "
                            + "Configúralo antes de registrar los webhooks.");
        }

        String callbackUrl = backendBaseUrl + "/zapsign/events";
        List<ZapSignWebhookHeaderDTO> headers = List.of(ZapSignWebhookHeaderDTO.builder()
                .name(zapSignConfig.getWebhookHeaderName())
                .value(webhookSecret)
                .build());

        List<ZapSignWebhookSetupResultDTO> results = new ArrayList<>();
        for (String type : EVENT_TYPES) {
            results.add(registerWebhook(callbackUrl, type, headers));
        }
        return ResponseEntity.ok(results);
    }

    private ZapSignWebhookSetupResultDTO registerWebhook(String url, String type, List<ZapSignWebhookHeaderDTO> headers) {
        try {
            ZapSignWebhookResponseDTO response = zapSignClient.createWebhook(ZapSignCreateWebhookRequestDTO.builder()
                    .url(url)
                    .type(type)
                    .headers(headers)
                    .build());
            return new ZapSignWebhookSetupResultDTO(type, true, response != null ? response.getId() : null, null);
        } catch (ZapSignApiException e) {
            log.error("[ZAPSIGN] No se pudo registrar el webhook type={}: {}", type, e.getMessage());
            return new ZapSignWebhookSetupResultDTO(type, false, null, e.getMessage());
        }
    }
}
