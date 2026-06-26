package com.verygana2.services.interfaces.details;

import com.verygana2.models.userDetails.ComplianceOfficerDetails;

public interface ComplianceOfficerDetailsService {
    ComplianceOfficerDetails getById(Long officerId);
    boolean existById(Long officerId);
}
