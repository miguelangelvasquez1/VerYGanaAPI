package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** Respuesta de POST /api/v1/checks/ y de GET /api/v1/checks/{check_id}/. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapSignCheckResponseDTO {

    @JsonProperty("check_id")
    private String checkId;

    /** not_started | in_progress | delayed | error | completed */
    @JsonProperty("status")
    private String status;

    @JsonProperty("score")
    private Double score;

    @JsonProperty("check_type")
    private String checkType;

    @JsonProperty("country")
    private String country;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("national_id")
    private String nationalId;

    @JsonProperty("tax_id")
    private String taxId;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("last_update_at")
    private String lastUpdateAt;

    @JsonProperty("pdf_report")
    private PdfReport pdfReport;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PdfReport {
        @JsonProperty("status")
        private String status;

        @JsonProperty("url")
        private String url;
    }
}
