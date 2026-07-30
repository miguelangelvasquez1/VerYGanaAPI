package com.verygana2.dtos.zapsign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/** Body de POST /api/v1/docs/ — crea un documento en ZapSign a partir de un PDF en base64. */
@Data
@Builder
public class ZapSignCreateDocumentRequestDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("base64_pdf")
    private String base64Pdf;

    @JsonProperty("signers")
    private List<ZapSignSignerRequestDTO> signers;

    @JsonProperty("lang")
    private String lang;

    @JsonProperty("brand_logo")
    private String brandLogo;

    @JsonProperty("brand_name")
    private String brandName;

    @JsonProperty("sandbox")
    private boolean sandbox;

    /** ID propio (contractId) para correlacionar el documento sin depender solo del token de ZapSign. */
    @JsonProperty("external_id")
    private String externalId;

    /** Carpeta en ZapSign donde queda el documento (ej. "/comercios/42/") — agrupa el historial de contratos por comercio. */
    @JsonProperty("folder_path")
    private String folderPath;
}
