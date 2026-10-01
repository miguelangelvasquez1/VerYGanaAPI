package com.verygana2.services.interfaces.finance;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutBankResponseDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;

public interface PayoutMethodService {

    /** Registra un nuevo método de pago. Dispara OTP automáticamente para NEQUI/DAVIPLATA. */
    EntityCreatedResponseDTO createPayoutMethod(Long commercialId, CreatePayoutMethodRequestDTO request);

    /** Catálogo de bancos/canales de Wompi Payouts, para elegir el bankCode al registrar BANK_TRANSFER. */
    List<PayoutBankResponseDTO> getAvailableBanks();

    /** Verifica el código OTP recibido por SMS (solo NEQUI/DAVIPLATA). */
    void verifyOtp(Long commercialId, Long payoutMethodId, String code);

    /** Reenvía el OTP (ej: si el código expiró). Solo aplicable en estado AWAITING_OTP. */
    void resendOtp(Long commercialId, Long payoutMethodId);

    /** Lista los métodos de pago del commercial. */
    PagedResponse<PayoutMethodResponseDTO> getByCommercialId(Long commercialId, Pageable pageable);

    /** Desactiva un método de pago (no lo elimina para mantener historial). */
    void deactivatePayoutMethod(Long commercialId, Long payoutMethodId);

    /** Marca un método de pago como el predeterminado para recibir payouts. Debe estar VERIFIED y activo. */
    void setDefaultPayoutMethod(Long commercialId, Long payoutMethodId);

    /**
     * Paso 1: prepara la subida de la certificación bancaria (PDF/foto) que el admin
     * usará para verificar titularidad. Solo aplica a BANK_ACCOUNT.
     */
    AssetUploadPermissionDTO prepareCertificateUpload(Long commercialId, Long payoutMethodId,
            FileUploadRequestDTO metadata);

    /** Paso 2: confirma la certificación ya subida a R2 y la asocia al método de pago. */
    EntityCreatedResponseDTO confirmCertificateUpload(Long commercialId, Long payoutMethodId,
            Long certificateAssetId);

    // ===== OPERACIONES DE ADMIN (BANK_TRANSFER) =====

    /** Admin aprueba un método BANK_TRANSFER pendiente de revisión. */
    void adminVerifyMethod(Long payoutMethodId);

    /** Admin rechaza un método, indicando el motivo. */
    void adminRejectMethod(Long payoutMethodId, String reason);

    /** Lista todos los métodos en un estado específico (para panel admin). */
    PagedResponse<PayoutMethodResponseDTO> getByStatus(
            com.verygana2.models.finance.PayoutMethod.VerificationStatus status, Pageable pageable);

    /** Proxy de streaming de la certificación bancaria privada, para el panel admin. */
    void streamCertificate(Long payoutMethodId, jakarta.servlet.http.HttpServletResponse response)
            throws IOException;
}
