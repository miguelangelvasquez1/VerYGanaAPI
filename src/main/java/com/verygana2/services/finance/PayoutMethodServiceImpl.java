package com.verygana2.services.finance;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutBankResponseDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.exceptions.payoutExceptions.InvalidPayoutMethodStateException;
import com.verygana2.exceptions.payoutExceptions.OtpVerificationException;
import com.verygana2.exceptions.payoutExceptions.PayoutMethodNotFoundException;
import com.verygana2.mappers.finance.PayoutMethodMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.DocType;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.PayoutMethodCertificateAssetRepository;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PayoutMethodService;
import com.verygana2.services.wompi.WompiPayoutClient;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutMethodServiceImpl implements PayoutMethodService {

    private static final Pattern NUMERIC_DOC = Pattern.compile("^\\d{5,15}$");
    private static final Pattern NIT_DOC = Pattern.compile("^\\d{6,12}(-\\d)?$");
    private static final Pattern PASSPORT_DOC = Pattern.compile("^[A-Za-z0-9]{5,20}$");

    private static final Set<SupportedMimeType> ALLOWED_CERTIFICATE_MIME_TYPES = Set.of(
            SupportedMimeType.APPLICATION_PDF, SupportedMimeType.IMAGE_JPEG,
            SupportedMimeType.IMAGE_PNG, SupportedMimeType.IMAGE_WEBP);
    private static final long MAX_CERTIFICATE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final CommercialDetailsService commercialDetailsService;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final PayoutMethodRepository payoutMethodRepository;
    private final PayoutMethodCertificateAssetRepository payoutMethodCertificateAssetRepository;
    private final PayoutMethodMapper payoutMethodMapper;
    private final TwilioSmsService twilioSmsService;
    private final ScreeningService screeningService;
    private final WompiPayoutClient wompiPayoutClient;
    private final R2Service r2Service;
    private final AssetOrphanedService assetOrphanedService;

    // ===== COMMERCIAL =====

    @Override
    @Transactional
    public EntityCreatedResponseDTO createPayoutMethod(Long commercialId, CreatePayoutMethodRequestDTO request) {
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

        validateFieldsByType(request);

        PayoutMethod method = payoutMethodMapper.toPayoutMethod(request);
        method.setCommercial(commercial);

        if (request.getType() == PayoutMethodType.BANK_ACCOUNT) {
            // BANK_TRANSFER va directo a revisión manual del admin
            method.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        }

        PayoutMethod saved = payoutMethodRepository.save(method);

        // Para NEQUI/DAVIPLATA, se dispara el OTP automáticamente
        if (request.getType() == PayoutMethodType.NEQUI || request.getType() == PayoutMethodType.DAVIPLATA) {
            try {
                twilioSmsService.sendOtp(saved.getPhoneNumber());
                saved.setVerificationStatus(VerificationStatus.AWAITING_OTP);
                payoutMethodRepository.save(saved);
                log.info("OTP enviado para método de pago id={}", saved.getId());
            } catch (Exception e) {
                log.error("Error enviando OTP para método id={}: {}", saved.getId(), e.getMessage());
                // El método queda en PENDING_VERIFICATION; el commercial puede usar /resend-otp
            }
        }

        String message = switch (request.getType()) {
            case NEQUI, DAVIPLATA ->
                "Método registrado. Revisa el SMS en tu número registrado para confirmar el código OTP.";
            case BANK_ACCOUNT ->
                "Método registrado. Quedará disponible una vez que nuestro equipo verifique los datos bancarios.";
        };

        return new EntityCreatedResponseDTO(saved.getId(), message, Instant.now());
    }

    @Override
    public List<PayoutBankResponseDTO> getAvailableBanks() {
        return wompiPayoutClient.getBanks().stream()
                .map(bank -> new PayoutBankResponseDTO(bank.getId(), bank.getName()))
                .toList();
    }

    @Override
    @Transactional
    public void verifyOtp(Long commercialId, Long payoutMethodId, String code) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (method.getVerificationStatus() != VerificationStatus.AWAITING_OTP) {
            throw new InvalidPayoutMethodStateException(
                "El método no está esperando verificación OTP. Estado actual: " + method.getVerificationStatus());
        }

        boolean approved = twilioSmsService.verifyOtp(method.getPhoneNumber(), code);

        if (!approved) {
            throw new OtpVerificationException(
                "Código OTP incorrecto o expirado. Solicita un nuevo código con /resend-otp.");
        }

        // Mismo screening SARLAFT/OFAC que se exige a BANK_TRANSFER antes de
        // dejar un método listo para recibir dinero (ver adminVerifyMethod).
        Long commercialUserId = method.getCommercial().getUser().getId();
        try {
            screeningService.screenOrThrow(
                    commercialUserId,
                    method.getAccountHolderName(),
                    method.getAccountHolderDoc());
        } catch (com.verygana2.exceptions.compliance.ScreeningHitException e) {
            method.reject("Rechazado automáticamente por screening: " + e.getMessage());
            payoutMethodRepository.save(method);
            throw new IllegalStateException("Método de pago rechazado: el titular aparece en listas restrictivas.");
        }

        method.markVerified();
        payoutMethodRepository.save(method);
        assignDefaultIfAbsent(method);
        log.info("Método de pago id={} verificado vía OTP para commercial id={}", payoutMethodId, commercialId);
    }

    @Override
    @Transactional
    public void resendOtp(Long commercialId, Long payoutMethodId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (method.getVerificationStatus() != VerificationStatus.AWAITING_OTP
                && method.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new InvalidPayoutMethodStateException(
                "Solo se puede reenviar el OTP si el método está en estado AWAITING_OTP o PENDING_VERIFICATION.");
        }

        twilioSmsService.sendOtp(method.getPhoneNumber());
        method.setVerificationStatus(VerificationStatus.AWAITING_OTP);
        payoutMethodRepository.save(method);
        log.info("OTP reenviado para método id={}", payoutMethodId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PayoutMethodResponseDTO> getByCommercialId(Long commercialId, Pageable pageable) {
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);
        Long defaultMethodId = commercial.getDefaultPayoutMethod() != null
                ? commercial.getDefaultPayoutMethod().getId()
                : null;

        return PagedResponse.from(
            payoutMethodRepository.findByCommercialId(commercialId, pageable)
                .map(method -> {
                    PayoutMethodResponseDTO dto = payoutMethodMapper.toPayoutMethodResponseDTO(method);
                    dto.setDefaultMethod(method.getId().equals(defaultMethodId));
                    return dto;
                })
        );
    }

    @Override
    @Transactional
    public void deactivatePayoutMethod(Long commercialId, Long payoutMethodId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);
        method.setActive(false);
        payoutMethodRepository.save(method);
        log.info("Método de pago id={} desactivado por commercial id={}", payoutMethodId, commercialId);
    }

    @Override
    @Transactional
    public void setDefaultPayoutMethod(Long commercialId, Long payoutMethodId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (!method.canBeUsedForPayout()) {
            throw new InvalidPayoutMethodStateException(
                "Solo un método VERIFIED y activo puede marcarse como predeterminado. Estado actual: "
                + method.getVerificationStatus());
        }

        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);
        commercial.setDefaultPayoutMethod(method);
        commercialDetailsRepository.save(commercial);

        log.info("Método de pago id={} marcado como predeterminado para commercial id={}", payoutMethodId, commercialId);
    }

    @Override
    @Transactional
    public AssetUploadPermissionDTO prepareCertificateUpload(Long commercialId, Long payoutMethodId,
            FileUploadRequestDTO metadata) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (method.getType() != PayoutMethodType.BANK_ACCOUNT) {
            throw new IllegalArgumentException(
                "Solo los métodos BANK_ACCOUNT requieren certificación bancaria. NEQUI/DAVIPLATA se verifican por OTP.");
        }

        validateCertificateMetadata(metadata);

        String objectKey = generatePayoutMethodCertificateObjectKey(payoutMethodId, metadata);

        PayoutMethodCertificateAsset asset = PayoutMethodCertificateAsset.builder()
                .objectKey(objectKey)
                .sizeBytes(metadata.getSizeBytes())
                .status(AssetStatus.PENDING)
                .uploadedAt(ZonedDateTime.now())
                .payoutMethod(null)
                .build();

        PayoutMethodCertificateAsset saved = payoutMethodCertificateAssetRepository.save(asset);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, metadata.getContentType());

        return AssetUploadPermissionDTO.builder()
                .assetId(saved.getId())
                .imagePermission(permission)
                .build();
    }

    @Override
    @Transactional
    public EntityCreatedResponseDTO confirmCertificateUpload(Long commercialId, Long payoutMethodId,
            Long certificateAssetId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        PayoutMethodCertificateAsset asset = null;
        try {
            asset = payoutMethodCertificateAssetRepository.findById(certificateAssetId)
                    .orElseThrow(() -> new ValidationException("Asset no encontrado: " + certificateAssetId));

            if (asset.getPayoutMethod() != null) {
                throw new ValidationException("Asset ya está asociado a un método de pago: " + asset.getId());
            }

            if (asset.getStatus() != AssetStatus.PENDING) {
                throw new ValidationException("Asset no está en estado válido: " + asset.getStatus());
            }

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    true,
                    asset.getObjectKey(),
                    asset.getSizeBytes(),
                    MAX_CERTIFICATE_SIZE_BYTES,
                    ALLOWED_CERTIFICATE_MIME_TYPES);

            asset.setMimeType(realMimeType);
            asset.setStatus(AssetStatus.VALIDATED);

            // Si ya había una certificación (reemplazo), la anterior queda huérfana
            payoutMethodCertificateAssetRepository.findByPayoutMethodId(payoutMethodId)
                    .ifPresent(oldAsset -> {
                        oldAsset.setPayoutMethod(null);
                        payoutMethodCertificateAssetRepository.save(oldAsset);
                        assetOrphanedService.markPayoutMethodCertificateAssetsAsOrphanedByIds(List.of(oldAsset.getId()));
                    });

            asset.setPayoutMethod(method);
            payoutMethodCertificateAssetRepository.save(asset);

            log.info("Certificación bancaria confirmada para método id={}", payoutMethodId);

            return new EntityCreatedResponseDTO(payoutMethodId,
                    "Certificación bancaria subida correctamente", Instant.now());

        } catch (Exception e) {
            if (asset != null) {
                log.error("Error confirmando certificación, marcando asset como huérfano: {}", asset.getId());
                assetOrphanedService.markPayoutMethodCertificateAssetsAsOrphanedByIds(List.of(asset.getId()));
            }
            throw e;
        }
    }

    // ===== ADMIN =====

    @Override
    @Transactional
    public void adminVerifyMethod(Long payoutMethodId) {
        PayoutMethod method = payoutMethodRepository.findById(payoutMethodId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));

        if (method.getVerificationStatus() != VerificationStatus.UNDER_REVIEW) {
            throw new InvalidPayoutMethodStateException(
                "Solo se pueden verificar métodos en estado UNDER_REVIEW. Estado actual: "
                + method.getVerificationStatus());
        }

        Long commercialUserId = method.getCommercial().getUser().getId();
        try {
            screeningService.screenOrThrow(
                    commercialUserId,
                    method.getAccountHolderName(),
                    method.getAccountHolderDoc());
        } catch (com.verygana2.exceptions.compliance.ScreeningHitException e) {
            method.reject("Rechazado automáticamente por screening: " + e.getMessage());
            payoutMethodRepository.save(method);
            throw new IllegalStateException("Método de pago rechazado: el titular aparece en listas restrictivas.");
        }

        method.markVerified();
        payoutMethodRepository.save(method);
        assignDefaultIfAbsent(method);
        log.info("Admin verificó método de pago id={}", payoutMethodId);
    }

    @Override
    @Transactional
    public void adminRejectMethod(Long payoutMethodId, String reason) {
        PayoutMethod method = payoutMethodRepository.findById(payoutMethodId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));

        method.reject(reason);
        payoutMethodRepository.save(method);
        log.info("Admin rechazó método de pago id={} — motivo: {}", payoutMethodId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PayoutMethodResponseDTO> getByStatus(VerificationStatus status, Pageable pageable) {
        return PagedResponse.from(
            payoutMethodRepository.findByVerificationStatus(status, pageable)
                .map(method -> {
                    PayoutMethodResponseDTO dto = payoutMethodMapper.toPayoutMethodResponseDTO(method);
                    dto.setCertificateUrl(resolveCertificateUrl(method));
                    return dto;
                })
        );
    }

    @Override
    @Transactional(readOnly = true)
    public void streamCertificate(Long payoutMethodId, HttpServletResponse response) throws IOException {
        PayoutMethod method = payoutMethodRepository.findById(payoutMethodId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));

        PayoutMethodCertificateAsset asset = method.getCertificateAsset();
        if (asset == null) {
            throw new EntityNotFoundException(
                "El método de pago no tiene certificación bancaria: " + payoutMethodId);
        }

        log.info("Streaming certificación bancaria: payoutMethodId={}, objectKey=private/{}",
                payoutMethodId, asset.getObjectKey());

        try (var stream = r2Service.getPrivateObjectStream(asset.getObjectKey())) {
            String contentType = stream.response().contentType();
            response.setContentType(contentType != null ? contentType : "application/pdf");
            Long contentLength = stream.response().contentLength();
            if (contentLength != null && contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            response.setHeader("Cache-Control", "private, max-age=300");
            stream.transferTo(response.getOutputStream());
        }
    }

    // ===== HELPERS =====

    /**
     * URL del proxy privado para ver la certificación, solo si ya fue subida y
     * validada — evita mostrar al admin un asset todavía PENDING (subida a medias).
     */
    private String resolveCertificateUrl(PayoutMethod method) {
        PayoutMethodCertificateAsset asset = method.getCertificateAsset();
        if (asset == null || asset.getStatus() != AssetStatus.VALIDATED) {
            return null;
        }
        return appBaseUrl + "/admin/payout-methods/" + method.getId() + "/certificate";
    }

    private PayoutMethod getOwnedMethod(Long commercialId, Long payoutMethodId) {
        return payoutMethodRepository.findByIdAndCommercialId(payoutMethodId, commercialId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));
    }

    /**
     * Si el commercial todavía no tiene ningún método por defecto, el primero que
     * se verifica exitosamente (NEQUI/DAVIPLATA por OTP o BANK_ACCOUNT por admin)
     * queda marcado como predeterminado automáticamente — mejor UX, no obliga al
     * commercial a un paso manual extra para su único método. A partir del
     * segundo método verificado, ya hay un default existente, así que el
     * commercial debe usar setDefaultPayoutMethod explícitamente para cambiarlo.
     */
    private void assignDefaultIfAbsent(PayoutMethod method) {
        CommercialDetails commercial = method.getCommercial();
        if (commercial.getDefaultPayoutMethod() == null) {
            commercial.setDefaultPayoutMethod(method);
            commercialDetailsRepository.save(commercial);
            log.info("Método de pago id={} marcado automáticamente como predeterminado (primer método verificado) para commercial id={}",
                    method.getId(), commercial.getId());
        }
    }

    /**
     * Valida que los campos requeridos por cada tipo de método estén presentes.
     * Wompi rechaza con D07/D11/D34 si falta alguno de estos datos.
     */
    private void validateFieldsByType(CreatePayoutMethodRequestDTO req) {
        validateDocFormat(req.getAccountHolderDocType(), req.getAccountHolderDoc());

        switch (req.getType()) {
            case BANK_ACCOUNT -> {
                if (req.getBankCode() == null || req.getBankCode().isBlank())
                    throw new IllegalArgumentException("bankCode es requerido para BANK_TRANSFER");
                if (req.getAccountNumber() == null || req.getAccountNumber().isBlank())
                    throw new IllegalArgumentException("accountNumber es requerido para BANK_TRANSFER");
                if (req.getBankAccountType() == null)
                    throw new IllegalArgumentException("bankAccountType es requerido para BANK_TRANSFER");

                // El bankCode es el bankId (UUID) del catálogo GET /banks de Wompi, no un
                // código ACH: si no existe en el catálogo real, el payout fallará el día
                // que se ejecute. Se valida contra Wompi aquí, al momento del registro.
                boolean bankExists = wompiPayoutClient.getBanks().stream()
                        .anyMatch(bank -> req.getBankCode().equals(bank.getId()));
                if (!bankExists)
                    throw new IllegalArgumentException(
                        "bankCode no corresponde a un banco válido del catálogo de Wompi. "
                        + "Consulta GET /api/commercial/payout-methods/banks para ver las opciones válidas.");
            }
            case NEQUI, DAVIPLATA -> {
                if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank())
                    throw new IllegalArgumentException("phoneNumber es requerido para " + req.getType());
                if (!req.getPhoneNumber().replaceAll("[^0-9]", "").matches("^3\\d{9}$"))
                    throw new IllegalArgumentException(
                        "phoneNumber debe ser un número colombiano válido de 10 dígitos (ej: 3001234567)");
            }
        }
    }

    /**
     * Valida el formato del documento del titular según su tipo, antes de que
     * Wompi lo rechace en el payout (registrado semanas/meses después).
     */
    private void validateDocFormat(DocType docType, String doc) {
        boolean valid = switch (docType) {
            case CC, CE, TI -> NUMERIC_DOC.matcher(doc).matches();
            case NIT -> NIT_DOC.matcher(doc).matches();
            case PP -> PASSPORT_DOC.matcher(doc).matches();
        };
        if (!valid) {
            throw new IllegalArgumentException(
                "accountHolderDoc no tiene un formato válido para el tipo de documento " + docType);
        }
    }

    private void validateCertificateMetadata(FileUploadRequestDTO metadata) {
        if (metadata.getSizeBytes() > MAX_CERTIFICATE_SIZE_BYTES) {
            throw new ValidationException(
                "Archivo muy grande. Máximo permitido: " + (MAX_CERTIFICATE_SIZE_BYTES / (1024 * 1024)) + " MB");
        }

        boolean isAllowed = ALLOWED_CERTIFICATE_MIME_TYPES.stream()
                .anyMatch(mime -> mime.getMime().equals(metadata.getContentType()));

        if (!isAllowed) {
            throw new ValidationException("Tipo de archivo no permitido: " + metadata.getContentType());
        }
    }

    private String generatePayoutMethodCertificateObjectKey(Long payoutMethodId, FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        return String.format("payout-methods/%d/%s-%s%s", payoutMethodId, timestamp, uuid, extension);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }
}
