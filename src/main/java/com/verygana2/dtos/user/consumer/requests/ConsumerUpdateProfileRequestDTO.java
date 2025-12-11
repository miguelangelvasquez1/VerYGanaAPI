package com.verygana2.dtos.user.consumer.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConsumerUpdateProfileRequestDTO {
    @NotBlank(message = "Email cannot be empty")
    @Email (message = "Invalid email format")
    private String email;
    @NotBlank(message = "Phone number cannot be empty")
    @Size(min = 10, max = 10, message = "Phone number must have 10 digits")
    private String phoneNumber;
    @NotBlank(message = "Department cannot be empty")
    private String department;
    @NotBlank(message = "Municipality cannot be empty")
    private String municipality;
}
