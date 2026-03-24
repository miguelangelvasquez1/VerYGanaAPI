package com.verygana2.dtos.user;

import java.time.LocalDate;
import java.util.List;

import com.verygana2.models.Category;

import com.verygana2.models.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ConsumerRegisterDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Municipality is required")
    private String municipality;

    @Size(min = 1, message = "At least one preference must be selected")
    @NotNull(message = "Preferences are required")
    private List<Category> categories;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9._]+$",
            message = "Username only allows letters, numbers, dot and underscore"
    )
    private String userName;

    private String referredByCode;

    @NotNull(message = "Avatar is required")
    private Long avatarId;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @NotNull(message = "Gender is required")
    private Gender gender;

}