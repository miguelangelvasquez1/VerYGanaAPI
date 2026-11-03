package com.verygana2.dtos.tax;

import java.time.LocalDate;

import com.verygana2.models.enums.tax.EstablishmentRelation;
import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxCatalogType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Peticiones de establecimientos, revisión de secciones y catálogo tributario. */
public final class TaxRequestDTOs {

    private TaxRequestDTOs() {
    }

    @Data
    public static class EstablishmentRequest {
        @NotBlank
        @Size(max = 150)
        private String name;

        @NotBlank
        @Size(max = 300)
        private String address;

        @NotBlank
        @Pattern(regexp = "\\d{5}", message = "Código DANE del municipio (5 dígitos)")
        private String municipalityCode;

        @NotNull
        private EstablishmentRelation relation;
    }

    @Data
    public static class CloseEstablishmentRequest {
        @NotBlank
        @Size(max = 300)
        private String reason;
    }

    @Data
    public static class SectionReviewRequest {
        @NotNull
        private SectionReviewResult result;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    public static class CatalogEntryRequest {
        @NotNull
        private TaxCatalogType catalogType;

        @NotBlank
        @Size(max = 30)
        private String code;

        @NotBlank
        @Size(max = 200)
        private String label;

        /** Norma o fuente que respalda el código. */
        @NotBlank
        @Size(max = 500)
        private String legalBasis;

        @NotNull
        private LocalDate validFrom;
    }

    @Data
    public static class RetireCatalogEntryRequest {
        /** Último día de vigencia. */
        @NotNull
        private LocalDate validTo;
    }
}
