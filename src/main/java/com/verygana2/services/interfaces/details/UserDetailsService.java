package com.verygana2.services.interfaces.details;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.admin.EditBasicInfoRequestDTO;
import com.verygana2.dtos.user.admin.UserSummaryResponseDTO;
import com.verygana2.models.enums.Role;
import com.verygana2.models.userDetails.UserDetails;

public interface UserDetailsService {
    UserDetails getUserById (Long userId);
    Integer countActiveUsersByRole (Role role);
    PagedResponse<UserSummaryResponseDTO> getNewUsers (ZonedDateTime startDate, ZonedDateTime endDate, String search, Pageable pageable);
    void blockUser (UUID publicId, String reason);
    void unblockUser (UUID publicId, String reason);
    EntityUpdatedResponseDTO editBasicInfo (UUID publicId, EditBasicInfoRequestDTO request);
}
