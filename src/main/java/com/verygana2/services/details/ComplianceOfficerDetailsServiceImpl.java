package com.verygana2.services.details;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerSummaryResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ComplianceOfficerDetails;
import com.verygana2.repositories.details.ComplianceOfficerDetailsRepository;
import com.verygana2.services.interfaces.details.ComplianceOfficerDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplianceOfficerDetailsServiceImpl implements ComplianceOfficerDetailsService {

    private final ComplianceOfficerDetailsRepository complianceOfficerDetailsRepository;
    private final UserMapper userMapper;

    @Override
    public ComplianceOfficerDetails getById(Long officerId) {
        return complianceOfficerDetailsRepository.findById(Objects.requireNonNull(officerId))
                .orElseThrow(() -> new EntityNotFoundException("Compliance officer with id: " + officerId + " not found"));
    }

    @Override
    public boolean existById(Long officerId) {
        return complianceOfficerDetailsRepository.existsById(Objects.requireNonNull(officerId));
    }

    @Override
    public PagedResponse<ComplianceOfficerSummaryResponseDTO> getComplianceOfficers(String search, UserState userState,
            Pageable pageable) {

        return PagedResponse.from(complianceOfficerDetailsRepository.findComplianceOfficers(search, userState, pageable).map(userMapper::toComplianceOfficerSummaryResponseDTO));
    }

    @Override
    public ComplianceOfficerResponseDTO getComplianceOfficer(UUID publicId) {
        
        return userMapper.toComplianceOfficerResponseDTO(complianceOfficerDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Compliance officer with public id: " + publicId + " not found")));
    }
}