package com.verygana2.services.interfaces.details;

import com.verygana2.models.userDetails.AdminDetails;

public interface AdminDetailsService {
    
    AdminDetails getById (Long adminId);
    boolean existById (Long adminId);
}
