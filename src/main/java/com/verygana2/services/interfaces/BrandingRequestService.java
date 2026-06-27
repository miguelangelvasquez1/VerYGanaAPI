package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.ApproveBrandingRequestDTO;
import com.verygana2.dtos.branding.AssignDesignerDTO;
import com.verygana2.dtos.branding.BrandingGameDTO;
import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.ConfirmCorporateResourceDTO;
import com.verygana2.dtos.branding.CorporateResourceUploadPermissionDTO;
import com.verygana2.dtos.branding.CreateBrandingRequestDTO;
import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.branding.RejectBrandingRequestDTO;
import com.verygana2.dtos.branding.RequestDesignChangesDTO;
import com.verygana2.dtos.branding.UpdateBrandingRequestConfigDTO;

public interface BrandingRequestService {

    // Catálogo de juegos
    Page<BrandingGameDTO> getGamesForBranding(Pageable pageable);

    // Anunciante
    BrandingRequestSummaryDTO createBrandingRequest(CreateBrandingRequestDTO dto, Long commercialUserId);

    BrandingRequestDetailDTO getBrandingRequestDetail(Long requestId, Long userId);

    CorporateResourceUploadPermissionDTO generateResourceUploadUrl(Long requestId, FileUploadRequestDTO dto, Long userId);

    void confirmCorporateResource(Long requestId, ConfirmCorporateResourceDTO dto, Long userId);

    void submitForReview(Long requestId, Long userId);

    void updateConfig(Long requestId, UpdateBrandingRequestConfigDTO dto, Long userId);

    List<BrandingRequestSummaryDTO> getMyBrandingRequests(Long commercialUserId);

    // Admin
    BrandingRequestDetailDTO getAdminBrandingRequestDetail(Long requestId);

    List<GameDesignerSummaryDTO> getActiveDesigners();

    void assignDesigner(Long requestId, AssignDesignerDTO dto, Long adminUserId);

    void approveBrandingRequest(Long requestId, ApproveBrandingRequestDTO dto, Long adminUserId);

    void rejectBrandingRequest(Long requestId, RejectBrandingRequestDTO dto, Long adminUserId);

    List<BrandingRequestSummaryDTO> getAllBrandingRequests();

    // Revisión del anunciante sobre el diseño
    void approveDesign(Long requestId, Long userId);
    void requestDesignChanges(Long requestId, Long userId, RequestDesignChangesDTO dto);
    String getPreviewUrl(Long requestId, Long userId);
}
