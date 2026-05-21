package com.verygana2.services.details;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.services.interfaces.details.AdminDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDetailsServiceImpl implements AdminDetailsService{

    private final AdminDetailsRepository adminDetailsRepository;
    
    @Override
    public AdminDetails getById(Long adminId) {
        return adminDetailsRepository.findById(Objects.requireNonNull(adminId)).orElseThrow(() -> new EntityNotFoundException("Admin with id: " + adminId + " not found "));
    }

    @Override
    public boolean existById(Long adminId) {
        return adminDetailsRepository.existsById(Objects.requireNonNull(adminId));
    }
    
}
