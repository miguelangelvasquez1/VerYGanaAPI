package com.verygana2.dtos.zapsign;

/** Resultado de registrar un webhook en ZapSign — uno por cada "type" solicitado. */
public record ZapSignWebhookSetupResultDTO(String type, boolean success, String webhookId, String errorMessage) {
}
