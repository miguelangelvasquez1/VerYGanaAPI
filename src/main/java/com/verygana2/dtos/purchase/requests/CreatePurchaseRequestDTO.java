package com.verygana2.dtos.purchase.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    
    @NotEmpty(message = "Purchase must have at least one item")
    @Valid
    private List<CreatePurchaseItemRequestDTO> items;
    
    // ===== INFORMACIÓN DE ENTREGA =====
    
    @NotBlank(message = "Delivery address is required")
    @Size(max = 255, message = "Address is too long")
    private String deliveryAddress;
    
    @NotBlank(message = "Delivery city is required")
    @Size(max = 100, message = "City name is too long")
    private String deliveryCity;
    
    @NotBlank(message = "Delivery department is required")
    @Size(max = 100, message = "Department name is too long")
    private String deliveryDepartment;
    
    @NotBlank(message = "Delivery phone is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String deliveryPhone;
    
    @Size(max = 500, message = "Delivery notes are too long")
    private String deliveryNotes;  // Opcional
    
    // ===== DESCUENTOS (OPCIONAL) =====
    
    private String couponCode;  // Código de cupón si aplica
    
}
