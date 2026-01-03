package com.verygana2.dtos.wompi;

import com.verygana2.models.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WompiDepositRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "5000", message = "Minimum amount: $5.000 COP")
    @DecimalMax(value = "10000000", message = "Maximum amount: $10.000.000 COP")
    private java.math.BigDecimal amount;
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod; // CARD, NEQUI, PSE
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email not valid")
    private String email;
    
    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100)
    private String fullName;
    
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+57[0-9]{10}$", message = "Format: +573001234567")
    private String phone;
    
    // Solo para CARD
    private String cardToken;
    private Integer installments;
    
    // Solo para NEQUI
    private String nequiPhone;
    
    // Solo para PSE
    private Integer userType;      // 0=Natural, 1=Jurídica
    private String idType;         // CC, CE, NIT
    private String idNumber;
    private String bankCode;
}
