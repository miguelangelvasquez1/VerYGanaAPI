package com.verygana2.dtos.pqrs.requests;

import com.verygana2.models.enums.pqrs.PqrsResolutionAction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RespondPqrsRequestDTO {

    @NotBlank(message = "La respuesta es obligatoria")
    @Size(max = 4000, message = "La respuesta no debe superar los 4000 caracteres")
    private String response;

    /**
     * Requerido solo cuando el PQRS tiene un PurchaseItem vinculado (radicado
     * desde /purchaseItems/{id}/report): DISMISS (el ítem sigue su curso
     * normal) o REFUND (ver PurchaseItemRefundService). Se ignora en PQRS
     * genéricos.
     */
    private PqrsResolutionAction action;
}
