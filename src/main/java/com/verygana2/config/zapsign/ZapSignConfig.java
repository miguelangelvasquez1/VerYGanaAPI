package com.verygana2.config.zapsign;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Config del proveedor de firma electrónica ZapSign (único proveedor). No se valida con
 * @NotBlank porque, a diferencia de Wompi, si falta algún valor la llamada a la API falla
 * en tiempo de ejecución con un mensaje claro en vez de tumbar el arranque de la app.
 */
@Configuration
@ConfigurationProperties(prefix = "zapsign")
@Getter
@Setter
public class ZapSignConfig {

    private String apiToken;

    /**
     * true = usa la API sandbox de ZapSign (https://sandbox.api.zapsign.com.br) — documentos
     * sin validez legal y sin costo. La URL base se deriva de este flag en
     * ZapSignWebClientConfig; el token configurado debe pertenecer al mismo ambiente
     * (un token de sandbox no funciona contra producción y viceversa — ZapSign responde 403).
     */
    private boolean sandbox;

    /** Método de autenticación del firmante en ZapSign (ver auth_mode en su API). */
    private String defaultAuthMode = "assinaturaTela";

    private String lang = "es";

    /** Código de país por defecto para el teléfono del firmante, sin "+" (Colombia = "57"). */
    private String defaultPhoneCountryCode = "57";

    /** Mensaje incluido en el email/WhatsApp que ZapSign envía al firmante. */
    private String signerCustomMessage =
            "VERYGANA te invita a firmar electrónicamente el Contrato Marco de Vinculación Comercial.";

    /**
     * Si es true, además de pedirle al firmante foto del documento y selfie (siempre
     * requeridas), ZapSign valida biométricamente que la selfie corresponda a la persona
     * del documento (selfie_validation_type). Tiene costo adicional por firma en ZapSign
     * — por eso es una bandera aparte y no siempre está activa.
     */
    private boolean requireSelfieValidation = false;

    /** Tipo de validación biométrica a usar cuando requireSelfieValidation=true. */
    private String selfieValidationType = "liveness-document-match";

    private String brandLogo;

    private String brandName = "VERyGANA";

    /** País para las consultas de antecedentes (ZapSign checks): BR | CO | CL | MX | PE. */
    private String backgroundCheckCountry = "CO";

    /** Nombre del header con el que ZapSign firma sus webhooks (configurado al registrar el webhook). */
    private String webhookHeaderName = "X-VerYGana-Secret";

    /** Valor esperado en ese header; debe coincidir con el configurado al crear el webhook en ZapSign. */
    private String webhookSecret;
}
