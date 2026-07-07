package com.verygana2.services.details;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.ComplianceOfficerDetails;
import com.verygana2.repositories.details.ComplianceOfficerDetailsRepository;
import com.verygana2.services.interfaces.details.ComplianceOfficerDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplianceOfficerDetailsServiceImpl implements ComplianceOfficerDetailsService {

    private final ComplianceOfficerDetailsRepository complianceOfficerDetailsRepository;

    @Override
    public ComplianceOfficerDetails getById(Long officerId) {
        return complianceOfficerDetailsRepository.findById(Objects.requireNonNull(officerId))
                .orElseThrow(() -> new EntityNotFoundException("Compliance officer with id: " + officerId + " not found"));
    }

    @Override
    public boolean existById(Long officerId) {
        return complianceOfficerDetailsRepository.existsById(Objects.requireNonNull(officerId));
    }
}