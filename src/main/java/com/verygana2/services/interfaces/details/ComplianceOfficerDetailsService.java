package com.verygana2.services.interfaces.details;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerSummaryResponseDTO;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ComplianceOfficerDetails;

public interface ComplianceOfficerDetailsService {
    ComplianceOfficerDetails getById(Long officerId);
    boolean existById(Long officerId);
    PagedResponse<ComplianceOfficerSummaryResponseDTO> getComplianceOfficers (String search, UserState userState, Pageable pageable);
    ComplianceOfficerResponseDTO getComplianceOfficer (UUID publicId);
}
