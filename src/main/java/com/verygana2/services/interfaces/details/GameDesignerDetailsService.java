package com.verygana2.services.interfaces.details;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerSummaryResponseDTO;
import com.verygana2.models.enums.AccountStatus;

public interface GameDesignerDetailsService {
    PagedResponse<GameDesignerSummaryResponseDTO> getGameDesigners (String search, AccountStatus accountStatus, Pageable pageable);
    GameDesignerResponseDTO getGameDesigner (UUID publicId);
}
