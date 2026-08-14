package com.verygana2.services.interfaces.details;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.AdminResponseDTO;
import com.verygana2.dtos.user.admin.AdminSummaryResponseDTO;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.AdminDetails;

public interface AdminDetailsService {
    
    AdminDetails getById (Long adminId);
    boolean existById (Long adminId);
    PagedResponse<AdminSummaryResponseDTO> getAdmins (String search, UserState userState, Pageable pageable);
    AdminResponseDTO getAdmin (UUID publicId);
    void sendNotification (UUID publicId, String message);
    void sendNotifications (List<UUID> publicIds, String message);
}
