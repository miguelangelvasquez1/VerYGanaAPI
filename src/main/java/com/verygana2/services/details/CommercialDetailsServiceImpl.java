package com.verygana2.services.details;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.requests.CommercialUpdateProfileRequestDTO;
import com.verygana2.dtos.user.commercial.responses.CommercialOwnProfileResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.dtos.user.commercial.responses.PayoutReportResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.exceptions.EmailAlreadyExistsException;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.PhoneNumberAlreadyExistsException;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDetailsServiceImpl implements CommercialDetailsService {

    // Contratos ya generados y todavía sin firmar: su PDF nombra al representante legal vigente.
    private static final List<ContractStatus> CONTRACT_IN_FLIGHT_STATUSES = List.of(
            ContractStatus.PENDING_BUSINESS_REVIEW,
            ContractStatus.PENDING_VERYGANA_REVIEW,
            ContractStatus.APPROVED,
            ContractStatus.PENDING_SIGNATURE);

    private final CommercialDetailsRepository commercialDetailsRepository;
    private final CommercialContractRepository contractRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PurchaseItemService purchaseItemService;
    private final ProductReviewService productReviewService;
    private final ProductCategoryService productCategoryService;
    private final PayoutService payoutService;
    private final ApplicationEventPublisher eventPublisher;

    // @Lazy rompe el ciclo: CommercialDetailsService ↔ ProductService
    @Lazy
    private final ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        CommercialDetails commercialDetails = commercialDetailsRepository.findById(commercialId)
            .orElseThrow(() -> new ObjectNotFoundException("Commercial with id:" + commercialId + " not found", CommercialDetails.class));
        CommercialInitialDataResponseDTO initialData = userMapper.toCommercialInitialDataResponseDTO(commercialDetails);
        return initialData;
    }

    @Override
    public CommercialDetails getCommercialById(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        return commercialDetailsRepository.findByUser_Id(commercialId).orElseThrow(() -> new ObjectNotFoundException("The commercial with id: " + commercialId + " not found ", CommercialDetails.class));
    }

    @Override
    public CommercialDetails getCommercialByCompanyName(String companyName) {
        if (companyName.isBlank()) {
            throw new IllegalArgumentException("The company name cannot be empty");
        }
        return commercialDetailsRepository.findByCompanyName(companyName).orElseThrow(() -> new ObjectNotFoundException("The commercial with company name: " + companyName + " not found", CommercialDetails.class));
    }

    @Override
    public boolean existsCommercialById(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            return false;
        }
        return commercialDetailsRepository.existsById(commercialId);
    }

    @Override
    public PayoutReportResponseDTO getPayoutReport(Long commercialId, Integer year, Integer month) {

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime endDate = startDate.plusMonths(1);
        
        BigDecimal monthlyEarningsAmount = payoutService.getCommercialEarningsForDateRange(commercialId, startDate, endDate);
        BigDecimal commissionsAmount = purchaseItemService.getTotalPlatformComissionsByDateRange(commercialId, startDate, endDate);

        return PayoutReportResponseDTO.builder().commercialId(commercialId).month(month).earnings(monthlyEarningsAmount)
        .totalPlatformCommissionsAmount(commissionsAmount).year(year).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutReportResponseDTO[] getPayoutReports(Long commercialId, Integer year) {
        PayoutReportResponseDTO[] reports = new PayoutReportResponseDTO[12];
        for (int month = 1; month <= 12; month++) {
            reports[month - 1] = getPayoutReport(commercialId, year, month);
        }
        return reports;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportResponseDTO[] getSalesReports(Long commercialId, Integer year) {
        ZoneId zone = ZoneId.of("America/Bogota");
        SalesReportResponseDTO[] reports = new SalesReportResponseDTO[12];
        for (int month = 1; month <= 12; month++) {
            ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone);
            ZonedDateTime endDate = startDate.plusMonths(1);
            SalesReportResponseDTO monthlyReport = getSalesReport(commercialId, startDate, endDate);
            monthlyReport.setMonth(month);
            monthlyReport.setYear(year);
            reports[month - 1] = monthlyReport;
        }
        return reports;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportResponseDTO getSalesReport(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {

        BigDecimal salesAmount = purchaseItemService.getTotalCommercialSalesAmountByDateRange(commercialId, startDate, endDate);
        Integer salesCount = purchaseItemService.getTotalCommercialSalesByDateRange(commercialId, startDate, endDate);
        BigDecimal commissionsAmount = purchaseItemService.getTotalPlatformComissionsByDateRange(commercialId, startDate, endDate);
        var topSellingProducts = purchaseItemService
                .getTopSellingProductsByDateRangePage(commercialId, startDate, endDate, PageRequest.of(0, 5))
                .getData();

        return SalesReportResponseDTO.builder()
                .commercialId(commercialId)
                .startDate(startDate)
                .endDate(endDate)
                .totalSalesAmount(salesAmount)
                .totalSalesCount(salesCount)
                .totalPlatformCommissionsAmount(commissionsAmount)
                .topSellingProducts(topSellingProducts)
                .build();
    }

    @Override
    public Integer getSalesCount(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        getCommercialById(commercialId);
        return purchaseItemService.getTotalCommercialSalesByDateRange(commercialId, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DailySaleResponseDTO> getDailySales(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return purchaseItemService.getDailySalesPage(commercialId, startDate, endDate, pageable);
    }

    @Override
    public CommercialProfileResponseDTO getCommercialProfile(Long commercialId) {

        CommercialDetails commercial = getCommercialById(commercialId);
        CommercialProfileResponseDTO commercialProfile = userMapper.toCommercialProfileResponseDTO(commercial);
        commercialProfile.setAverageRate(productReviewService.getCommercialAvgRating(commercialId));
        commercialProfile.setReviewCount(productReviewService.getCommercialReviewCount(commercialId));
        commercialProfile.setProductCategories(productCategoryService.getCommercialProductCategories(commercialId));
        commercialProfile.setTotalActiveProducts(productService.getTotalCommercialProducts(commercialId, ProductStatus.ACTIVE));
        commercialProfile.setActiveProducts(productService.getCommercialProducts(commercialId, 0));
        
        return commercialProfile;

    }

    @Override
    public PagedResponse<CommercialSummaryResponseDTO> getCommercials(String search, UserState userState,
            PlanCode currentPlan, Pageable pageable) {

        return PagedResponse.from(commercialDetailsRepository.findCommercials(search, userState, currentPlan, pageable).map(userMapper::toCommercialSummaryResponseDTO));
    }

    @Override
    public CommercialResponseDTO getCommercial(UUID publicId) {
        return userMapper.toCommercialResponseDTO(commercialDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Commercial with public id: " + publicId + " not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public CommercialOwnProfileResponseDTO getCommercialOwnProfile(Long commercialId) {
        CommercialDetails commercial = getCommercialById(commercialId);
        User user = commercial.getUser();
        CommercialOnboarding onboarding = commercial.getOnboarding();

        return CommercialOwnProfileResponseDTO.builder()
                .companyName(commercial.getCompanyName())
                .nit(commercial.getNit())
                .mercantileRegistration(commercial.getMercantileRegistration())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(onboarding != null ? onboarding.getAddress() : null)
                .legalRepFirstName(onboarding != null ? onboarding.getLegalRepFirstName() : null)
                .legalRepLastName(onboarding != null ? onboarding.getLegalRepLastName() : null)
                .legalRepDocType(commercial.getLegalRepDocType())
                .legalRepDocNumber(commercial.getLegalRepDocNumber())
                .legalRepPepDeclaration(commercial.isPep())
                .whatsappAvailable(commercial.isWhatsappAvailable())
                .whatsappNumber(commercial.getWhatsappNumber())
                .build();
    }

    /**
     * Edición de perfil post-onboarding: contacto, domicilio, representante legal y
     * disponibilidad de WhatsApp. NIT/matrícula mercantil quedan fuera a propósito —
     * ver CommercialUpdateProfileRequestDTO.
     *
     * Cambiar el representante legal (nombre o documento) se rechaza mientras haya un contrato
     * en curso (su PDF ya nombra al representante anterior y el firmante se toma del actual) y
     * deja rastro de auditoría con lo anterior y lo nuevo.
     */
    @Override
    public EntityUpdatedResponseDTO updateCommercialProfile(Long commercialId, CommercialUpdateProfileRequestDTO request) {
        CommercialDetails commercial = getCommercialById(commercialId);
        User user = commercial.getUser();

        String newEmail = request.getEmail().trim();
        String newPhoneNumber = request.getPhoneNumber().trim();

        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyExistsException(newEmail);
        }
        if (!user.getPhoneNumber().equals(newPhoneNumber) && userRepository.existsByPhoneNumber(newPhoneNumber)) {
            throw new PhoneNumberAlreadyExistsException(newPhoneNumber);
        }

        boolean whatsappAvailable = Boolean.TRUE.equals(request.getWhatsappAvailable());
        if (whatsappAvailable && (request.getWhatsappNumber() == null || request.getWhatsappNumber().isBlank())) {
            throw new InvalidRequestException("Debe indicar el número de WhatsApp cuando está disponible");
        }

        CommercialOnboarding onboarding = commercial.getOnboarding();
        LegalRepChange legalRepChange = null;
        if (onboarding != null) {
            legalRepChange = detectLegalRepChange(commercial, onboarding, request);
            if (legalRepChange.changed()) {
                requireNoContractInFlight(commercialId);
            }
        }

        user.setEmail(newEmail);
        user.setPhoneNumber(newPhoneNumber);

        if (onboarding != null) {
            onboarding.setAddress(request.getAddress());
            onboarding.setLegalRepFirstName(legalRepChange.firstName());
            onboarding.setLegalRepLastName(legalRepChange.lastName());
            commercial.setLegalRepDocType(legalRepChange.docType());
            commercial.setLegalRepDocNumber(legalRepChange.docNumber());
            commercial.setPep(legalRepChange.pep());
        }

        commercial.setWhatsappAvailable(whatsappAvailable);
        commercial.setWhatsappNumber(whatsappAvailable ? request.getWhatsappNumber().trim() : null);

        try {
            userRepository.save(user);
            commercialDetailsRepository.save(commercial);
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad ante condiciones de carrera: dos ediciones simultáneas con el
            // mismo correo/teléfono pueden pasar ambas el chequeo existsBy* de arriba.
            throw new InvalidRequestException("El correo o el teléfono ya están registrados por otra cuenta.");
        }

        if (legalRepChange != null && legalRepChange.changed()) {
            publishLegalRepChangedAudit(commercialId, legalRepChange);
        }

        return EntityUpdatedResponseDTO.builder()
                .id(commercialId)
                .message("Perfil actualizado correctamente")
                .timestamp(Instant.now())
                .build();
    }

    // ==================== REPRESENTANTE LEGAL ====================

    /**
     * Valores nuevos (del request, ya sin espacios sobrantes) y anteriores del representante
     * legal. {@code identityChanged}: cambió la persona (nombre o documento). {@code changed}:
     * además incluye un cambio solo de la declaración PEP.
     */
    private record LegalRepChange(
            String firstName, String lastName, DocumentType docType, String docNumber, boolean pep,
            String previousFullName, DocumentType previousDocType, String previousDocNumber, boolean previousPep,
            boolean identityChanged, boolean changed) {
    }

    private LegalRepChange detectLegalRepChange(CommercialDetails commercial, CommercialOnboarding onboarding,
            CommercialUpdateProfileRequestDTO request) {
        String firstName = request.getLegalRepFirstName().trim();
        String lastName = request.getLegalRepLastName().trim();
        DocumentType docType = request.getLegalRepDocType();
        String docNumber = request.getLegalRepDocNumber().trim();
        boolean pep = Boolean.TRUE.equals(request.getLegalRepPepDeclaration());

        // Los valores guardados se recortan igual: el onboarding no los trimea, y comparar
        // "Juan " (guardado) contra "Juan" (request) marcaría como cambio un formulario sin tocar.
        String previousFirstName = trimOrNull(onboarding.getLegalRepFirstName());
        String previousLastName = trimOrNull(onboarding.getLegalRepLastName());
        DocumentType previousDocType = commercial.getLegalRepDocType();
        String previousDocNumber = trimOrNull(commercial.getLegalRepDocNumber());
        boolean previousPep = commercial.isPep();

        boolean identityChanged = !Objects.equals(firstName, previousFirstName)
                || !Objects.equals(lastName, previousLastName)
                || docType != previousDocType
                || !Objects.equals(docNumber, previousDocNumber);

        String previousFullName = ((previousFirstName != null ? previousFirstName : "") + " "
                + (previousLastName != null ? previousLastName : "")).trim();

        return new LegalRepChange(firstName, lastName, docType, docNumber, pep,
                previousFullName, previousDocType, previousDocNumber, previousPep,
                identityChanged, identityChanged || pep != previousPep);
    }

    /**
     * El PDF del contrato se renderiza una sola vez al generarlo y nombra al representante de
     * ese momento, pero al enviarlo a firma el firmante se toma del representante vigente
     * (ver ESignatureServiceImpl#requestSignature): cambiarlo con un contrato en revisión o
     * pendiente de firma dejaría un documento a nombre de una persona firmado por otra.
     */
    private void requireNoContractInFlight(Long commercialId) {
        if (contractRepository.existsByCommercial_IdAndStatusIn(commercialId, CONTRACT_IN_FLIGHT_STATUSES)) {
            throw new InvalidRequestException(
                    "No puede cambiar el representante legal mientras tiene un contrato en revisión o pendiente de firma. "
                            + "Espere a que se resuelva o cancélelo, y luego actualice el representante.");
        }
    }

    private void publishLegalRepChangedAudit(Long commercialId, LegalRepChange change) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("previousFullName", change.previousFullName());
            data.put("previousDocType", change.previousDocType() != null ? change.previousDocType().name() : null);
            data.put("previousDocNumber", change.previousDocNumber());
            data.put("previousPep", change.previousPep());
            data.put("newFullName", change.firstName() + " " + change.lastName());
            data.put("newDocType", change.docType().name());
            data.put("newDocNumber", change.docNumber());
            data.put("newPep", change.pep());
            data.put("identityChanged", change.identityChanged());

            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(commercialId)
                    .action("LEGAL_REPRESENTATIVE_CHANGED")
                    .level(AuditLevel.INFO)
                    .category("COMPLIANCE")
                    .description(change.identityChanged()
                            ? "Comercial cambió su representante legal desde la edición de perfil."
                            : "Comercial actualizó la declaración PEP de su representante legal.")
                    .className(CommercialDetailsServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(data)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría LEGAL_REPRESENTATIVE_CHANGED", e);
        }
    }

    private static String trimOrNull(String value) {
        return value != null ? value.trim() : null;
    }
}
