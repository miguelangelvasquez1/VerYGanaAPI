package com.verygana2.dtos.purchase.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePurchaseRequestDTO {
    
    // ===== ITEMS DE LA COMPRA =====
    @Valid
    @NotEmpty(message = "Purchase must have at least one item")
    private List<CreatePurchaseItemRequestDTO> items;
    
    // ===== INFORMACIÓN DE CONTACTO (OPCIONAL) =====
    // Solo para enviar las credenciales o códigos de activación
    @Valid
    private String contactEmail; // Email donde se enviarán las credenciales
    
    // ===== NOTAS ADICIONALES (OPCIONAL) =====
    private String notes; // Ej: "Enviar credenciales de Netflix al correo alternativo"

    // ===== DESCUENTOS (OPCIONAL) =====
    private String couponCode;  // Código de cupón si aplica
}