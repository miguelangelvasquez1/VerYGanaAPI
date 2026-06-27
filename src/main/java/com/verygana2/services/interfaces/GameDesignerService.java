package com.verygana2.services.interfaces;

import java.util.List;
import java.util.Map;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.dtos.branding.UpdateDesignerNotesDTO;
import com.verygana2.dtos.game.campaign.AssetConfirmRequest;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.user.gamedesigner.ChangePasswordDTO;
import com.verygana2.dtos.user.gamedesigner.GameDesignerProfileResponseDTO;
import com.verygana2.dtos.user.gamedesigner.ResetPasswordByEmailDTO;
import com.verygana2.dtos.user.gamedesigner.UpdateGameDesignerProfileDTO;

public interface GameDesignerService {

    // ===== Perfil =====
    GameDesignerProfileResponseDTO getMyProfile(Long userId);
    void updateProfile(Long userId, UpdateGameDesignerProfileDTO dto);

    // ===== Contraseña =====
    void changePassword(Long userId, ChangePasswordDTO dto);
    void resetPasswordByDesignerCode(ResetPasswordByEmailDTO dto);

    // ===== Solicitudes asignadas =====
    List<BrandingRequestSummaryDTO> getAssignedBrandingRequests(Long userId);
    DesignerBrandingDetailDTO getAssignedBrandingRequestDetail(Long requestId, Long userId);

    // ===== Assets del juego (RJSF) =====
    AssetUploadPermissionDTO generateUploadUrl(FileUploadRequestDTO request, Long userId);
    void confirmUpload(AssetConfirmRequest request);
    void deleteAsset(Long assetId, Long userId);
    String getPreviewUrl(Long requestId, Long userId);

    // ===== Flujo de branding =====
    void saveDraftFormData(Long requestId, Long userId, Map<String, Object> formData);
    void updateDesignerNotes(Long requestId, Long userId, UpdateDesignerNotesDTO dto);
    void submitDesignForReview(Long requestId, Long userId);
}
