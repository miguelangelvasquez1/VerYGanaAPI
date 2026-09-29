package com.verygana2.dtos.user.consumer.requests;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class ConsumerUpdateProfileRequestDTO {
    @Email(message = "Invalid email format")
    private String email;
    
    // phoneNumber YA NO se incluye aquí — se cambia vía OTP en endpoints separados
    
    private String department;
    
    private String municipalityName;
}
