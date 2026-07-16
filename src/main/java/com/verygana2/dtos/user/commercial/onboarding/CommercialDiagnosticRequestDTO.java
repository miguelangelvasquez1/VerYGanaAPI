package com.verygana2.dtos.user.commercial.onboarding;

import java.util.Set;

import com.verygana2.models.enums.commercial.PaymentPeriodicity;
import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Formulario inteligente: preguntas 3 a 12 del diagnóstico comercial. */
@Data
public class CommercialDiagnosticRequestDTO {

    @NotNull(message = "El objetivo principal es requerido")
    private PrimaryGoal primaryGoal; // Q3

    @NotNull(message = "Debe indicar si desea pagar una tarifa fija")
    private Boolean wantsFixedFee; // Q4

    @NotNull(message = "Debe indicar si acepta pagar comisión únicamente cuando exista una venta")
    private Boolean acceptsCommissionOnSaleOnly; // Q5

    @NotNull(message = "El porcentaje máximo cubierto con Llaves Promocionales es requerido")
    @Min(0)
    @Max(100)
    private Integer maxPromotionalKeysPercentage; // Q6

    @NotNull(message = "La comisión que acepta pagar a VERYGANA es requerida")
    @Min(0)
    @Max(100)
    private Integer acceptedCommissionPercentage; // Q7

    @NotNull(message = "Debe indicar si requiere juegos personalizados")
    private Boolean requiresCustomGames; // Q8

    private Set<TechIntegrationNeed> techIntegrationNeeds; // Q9 (vacío/null = ninguna)

    @NotNull(message = "Debe indicar si la actividad pertenece a un sector regulado")
    private Boolean regulatedSector; // Q10

    @NotNull(message = "Debe indicar si requiere negociación especial o aprobación corporativa previa")
    private Boolean requiresSpecialNegotiation; // Q11

    @Min(1)
    private Integer contractDurationMonths; // Q12a

    @NotNull(message = "La periodicidad de pago es requerida")
    private PaymentPeriodicity paymentPeriodicity; // Q12b

    @Size(max = 500)
    private String terminationTerms; // Q12c
}
