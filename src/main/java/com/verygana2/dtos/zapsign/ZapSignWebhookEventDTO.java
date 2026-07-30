package com.verygana2.dtos.zapsign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Payload que ZapSign envía a nuestro webhook. Compartido entre eventos de firma
 * (doc_signed) y de antecedentes (background_check_completed) — cada evento solo
 * llena los campos que le corresponden. Ref:
 * https://docs.zapsign.com.br/english/webhooks/como-funciona
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapSignWebhookEventDTO {

    @JsonProperty("event_type")
    private String eventType;

    // ── doc_signed ──────────────────────────────────────────────────────────

    /** Token del documento — es el envelopeId que guardamos en CommercialContract. */
    @JsonProperty("token")
    private String token;

    @JsonProperty("status")
    private String status;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("sandbox")
    private boolean sandbox;

    @JsonProperty("signers")
    private List<ZapSignSignerResponseDTO> signers;

    // ── background_check_completed ─────────────────────────────────────────

    @JsonProperty("check_id")
    private String checkId;
}
