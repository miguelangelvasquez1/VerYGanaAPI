package com.verygana2.controllers.commercial;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.ConfirmPayoutMethodCertificateUploadRequestDTO;
import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.requests.VerifyOtpRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutBankResponseDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/commercial/payout-methods")
@PreAuthorize("hasRole('COMMERCIAL')")
@RequiredArgsConstructor
public class PayoutMethodController {

    private final PayoutMethodService payoutMethodService;

    /**
     * Registra un nuevo método de pago.
     * - NEQUI/DAVIPLATA: envía OTP automáticamente al teléfono registrado.
     * - BANK_TRANSFER: queda en revisión manual (UNDER_REVIEW).
     *
     * POST /api/commercial/payout-methods
     */
    @PostMapping
    public ResponseEntity<EntityCreatedResponseDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePayoutMethodRequestDTO request) {

        Long commercialId = jwt.getClaim("userId");
        EntityCreatedResponseDTO response = payoutMethodService.createPayoutMethod(commercialId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista el catálogo de bancos/canales disponibles en Wompi Pagos a Terceros,
     * para que el frontend elija el bankCode correcto al registrar BANK_TRANSFER.
     *
     * GET /api/commercial/payout-methods/banks
     */
    @GetMapping("/banks")
    public ResponseEntity<List<PayoutBankResponseDTO>> getBanks() {
        return ResponseEntity.ok(payoutMethodService.getAvailableBanks());
    }

    /**
     * Confirma el código OTP recibido por SMS (solo NEQUI/DAVIPLATA).
     * El método pasa a estado VERIFIED si el código es correcto.
     *
     * POST /api/commercial/payout-methods/{id}/verify-otp
     */
    @PostMapping("/{id}/verify-otp")
    public ResponseEntity<Void> verifyOtp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody VerifyOtpRequestDTO request) {

        Long commercialId = jwt.getClaim("userId");
        payoutMethodService.verifyOtp(commercialId, id, request.getCode());
        return ResponseEntity.ok().build();
    }

    /**
     * Reenvía el OTP en caso de que el código anterior haya expirado.
     *
     * POST /api/commercial/payout-methods/{id}/resend-otp
     */
    @PostMapping("/{id}/resend-otp")
    public ResponseEntity<Void> resendOtp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        Long commercialId = jwt.getClaim("userId");
        payoutMethodService.resendOtp(commercialId, id);
        return ResponseEntity.ok().build();
    }

    /**
     * Lista todos los métodos de pago del commercial autenticado.
     *
     * GET /api/commercial/payout-methods?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<PagedResponse<PayoutMethodResponseDTO>> getAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long commercialId = jwt.getClaim("userId");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(payoutMethodService.getByCommercialId(commercialId, pageable));
    }

    /**
     * Desactiva un método de pago sin eliminarlo (mantiene el historial de payouts).
     *
     * PUT /api/commercial/payout-methods/{id}/deactivate
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        Long commercialId = jwt.getClaim("userId");
        payoutMethodService.deactivatePayoutMethod(commercialId, id);
        return ResponseEntity.ok().build();
    }

    /**
     * Marca un método de pago como el predeterminado para recibir payouts.
     * Solo se puede marcar como predeterminado un método VERIFIED y activo.
     *
     * PUT /api/commercial/payout-methods/{id}/set-default
     */
    @PutMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        Long commercialId = jwt.getClaim("userId");
        payoutMethodService.setDefaultPayoutMethod(commercialId, id);
        return ResponseEntity.ok().build();
    }

    /**
     * Paso 1 — Prepara la subida de la certificación bancaria (PDF/foto) que el
     * admin usará para verificar titularidad. Solo aplica a BANK_ACCOUNT.
     *
     * POST /api/commercial/payout-methods/{id}/certificate/prepare
     */
    @PostMapping("/{id}/certificate/prepare")
    public ResponseEntity<AssetUploadPermissionDTO> prepareCertificateUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody FileUploadRequestDTO metadata) {

        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(payoutMethodService.prepareCertificateUpload(commercialId, id, metadata));
    }

    /**
     * Paso 2 — Confirma la certificación ya subida a R2 y la asocia al método de pago.
     *
     * POST /api/commercial/payout-methods/{id}/certificate/confirm
     */
    @PostMapping("/{id}/certificate/confirm")
    public ResponseEntity<EntityCreatedResponseDTO> confirmCertificateUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmPayoutMethodCertificateUploadRequestDTO request) {

        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(
                payoutMethodService.confirmCertificateUpload(commercialId, id, request.getCertificateAssetId()));
    }
}
