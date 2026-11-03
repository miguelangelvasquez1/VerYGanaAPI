package com.verygana2.dtos.tax;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.enums.tax.IncomeConcept;
import com.verygana2.models.enums.tax.IncomeScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Envío de la ficha tributaria (incisos a–h del 4.7). Cada envío crea una versión nueva.
 * Los códigos (régimen, responsabilidades RUT, IVA, INC, método de facturación) son
 * opcionales, pero si vienen deben existir en el catálogo vigente.
 */
@Data
public class TaxProfileSubmitRequestDTO {

    // a) RUT
    /** Documento RUT del expediente que soporta la información. */
    private Long rutDocumentId;

    private List<@NotBlank @Size(max = 30) String> rutResponsibilityCodes = new ArrayList<>();

    // b) Régimen y actividades
    @Size(max = 30)
    private String regimeCode;

    @NotEmpty(message = "Indique al menos una actividad económica")
    @Valid
    private List<ActivityDTO> activities = new ArrayList<>();

    // c) IVA e INC
    @Size(max = 30)
    private String vatConditionCode;

    @Size(max = 30)
    private String incConditionCode;

    @Size(max = 1000)
    private String taxConditionScope;

    // d) Facturación y retención
    @NotNull(message = "Indique si está obligado a facturar")
    private Boolean invoicingObligated;

    @Size(max = 30)
    private String invoicingMethodCode;

    @Size(max = 1000)
    private String invoicingNotes;

    @NotNull(message = "Indique si es agente de retención")
    private Boolean withholdingAgent;

    @Size(max = 1000)
    private String withholdingNotes;

    // e) Ingresos o ventas (incluidas las operaciones fuera de VERYGANA)
    @NotEmpty(message = "Informe al menos un periodo de ingresos o ventas")
    @Valid
    private List<IncomeReportDTO> incomeReports = new ArrayList<>();

    // g) Capacidad de crecimiento
    @NotBlank(message = "Describa su capacidad operativa actual")
    @Size(max = 2000)
    private String currentCapacity;

    @Size(max = 2000)
    private String growthScenario;

    @Size(max = 2000)
    private String growthMeasures;

    // h) Asesoría contable o tributaria
    @NotNull(message = "Indique si cuenta con asesoría contable o tributaria")
    private Boolean hasAccountingAdvisor;

    @Size(max = 1000)
    private String advisorScope;

    @Data
    public static class ActivityDTO {
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "El código CIIU tiene 4 dígitos")
        private String ciiuCode;

        @Size(max = 300)
        private String description;

        @NotNull
        private Boolean main;
    }

    @Data
    public static class IncomeReportDTO {
        @NotNull
        private IncomeConcept concept;

        @NotNull
        private LocalDate periodStart;

        @NotNull
        private LocalDate periodEnd;

        @NotNull
        @PositiveOrZero
        private Long amountCents;

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "Moneda en código ISO de 3 letras (p. ej. COP)")
        private String currency;

        @NotNull
        private IncomeScope scope;

        /** Documento del expediente que soporta el valor (opcional). */
        private Long supportDocumentId;

        @Size(max = 500)
        private String notes;
    }
}
