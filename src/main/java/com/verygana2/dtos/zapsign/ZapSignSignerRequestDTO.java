package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/** Firmante dentro de un POST /api/v1/docs/ de ZapSign. */
@Data
@Builder
public class ZapSignSignerRequestDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    /** Método de autenticación del firmante: "tokenEmail", "assinaturaTela", "tokenSms", etc. */
    @JsonProperty("auth_mode")
    private String authMode;

    @JsonProperty("send_automatic_email")
    private boolean sendAutomaticEmail;

    /** Código de país sin "+" (ej. "57" para Colombia). */
    @JsonProperty("phone_country")
    private String phoneCountry;

    /** Número completo sin formato (sin espacios/guiones/código de país). */
    @JsonProperty("phone_number")
    private String phoneNumber;

    /** Mensaje personalizado incluido en el email/WhatsApp de invitación a firmar. */
    @JsonProperty("custom_message")
    private String customMessage;

    /** Si es true, el firmante no puede editar su nombre durante el proceso de firma. */
    @JsonProperty("lock_name")
    private boolean lockName;

    /**
     * Texto ancla dentro del documento (ej. "&lt;&lt;{firma_comercial}&gt;&gt;") donde ZapSign
     * coloca el widget de firma. Debe coincidir exactamente con el texto presente en el PDF.
     */
    @JsonProperty("signature_placement")
    private String signaturePlacement;

    /** El firmante debe tomarse una foto de su documento de identidad al firmar. */
    @JsonProperty("require_document_photo")
    private boolean requireDocumentPhoto;

    /** El firmante debe tomarse una selfie al firmar. */
    @JsonProperty("require_selfie_photo")
    private boolean requireSelfiePhoto;

    /**
     * Validación biométrica entre la selfie y la foto del documento (ej.
     * "liveness-document-match"). Null = se piden ambas fotos pero ZapSign no las
     * valida entre sí — controlado por ZapSignConfig.requireSelfieValidation.
     */
    @JsonProperty("selfie_validation_type")
    private String selfieValidationType;
}
