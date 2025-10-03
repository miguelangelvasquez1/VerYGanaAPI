package com.VerYGana.validator;

import org.springframework.lang.NonNull;
import org.springframework.validation.Validator;

import com.VerYGana.dtos2.UserDTO;

public class UserValidator implements Validator {

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return UserDTO.class.equals(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull org.springframework.validation.Errors errors) {
        UserDTO user = (UserDTO) target;

        // Add validation logic here
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            errors.rejectValue("email", "Email cannot be empty");
        }
        
        // Additional validation rules can be added here
    }
    
}
