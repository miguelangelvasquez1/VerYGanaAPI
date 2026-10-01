package com.verygana2.services.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.user.commercial.requests.CommercialUpdateProfileRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.mappers.UserMapper;
import com.verygana2.services.UserIdResolver;
import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.utils.audit.AuditEvent;

/**
 * {@code updateCommercialProfile}: cambiar el representante legal desde la edición de
 * perfil (nombre, documento y declaración PEP), con bloqueo por contrato en curso y
 * auditoría.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialDetailsServiceImpl.updateCommercialProfile — representante legal")
class CommercialDetailsServiceImplUpdateProfileTest {

    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialContractRepository contractRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PurchaseItemService purchaseItemService;
    @Mock private ProductReviewService productReviewService;
    @Mock private ProductCategoryService productCategoryService;
    @Mock private PayoutService payoutService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserIdResolver userIdResolver;
    @Mock private ProductService productService;

    private CommercialDetailsServiceImpl service;
    private CommercialDetails commercial;
    private CommercialOnboarding onboarding;

    private static final long COMMERCIAL_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new CommercialDetailsServiceImpl(
                commercialDetailsRepository, contractRepository, userRepository, userMapper,
                purchaseItemService, productReviewService, productCategoryService, payoutService,
                eventPublisher, userIdResolver, productService);

        User user = new User();
        user.setId(COMMERCIAL_ID);
        user.setEmail("empresa@example.com");
        user.setPhoneNumber("3001234567");

        onboarding = new CommercialOnboarding();
        onboarding.setLegalRepFirstName("Ana");
        onboarding.setLegalRepLastName("Pérez");

        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setUser(user);
        commercial.setOnboarding(onboarding);
        commercial.setLegalRepDocType(DocumentType.CC);
        commercial.setLegalRepDocNumber("111");
        commercial.setPep(false);

        lenient().when(commercialDetailsRepository.findByUser_Id(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
    }

    /** Request que no toca nada: mismos contacto y representante que el estado actual. */
    private CommercialUpdateProfileRequestDTO unchangedRequest() {
        CommercialUpdateProfileRequestDTO dto = new CommercialUpdateProfileRequestDTO();
        dto.setEmail("empresa@example.com");
        dto.setPhoneNumber("3001234567");
        dto.setAddress("Calle 1 # 2-3");
        dto.setLegalRepFirstName("Ana");
        dto.setLegalRepLastName("Pérez");
        dto.setLegalRepDocType(DocumentType.CC);
        dto.setLegalRepDocNumber("111");
        dto.setLegalRepPepDeclaration(false);
        dto.setWhatsappAvailable(false);
        return dto;
    }

    private CommercialUpdateProfileRequestDTO newPersonRequest() {
        CommercialUpdateProfileRequestDTO dto = unchangedRequest();
        dto.setLegalRepFirstName("Luis");
        dto.setLegalRepLastName("Gómez");
        dto.setLegalRepDocType(DocumentType.CE);
        dto.setLegalRepDocNumber("222");
        return dto;
    }

    @Test
    @DisplayName("sin cambios en el representante: no hay consulta de contratos ni auditoría")
    void unchangedLegalRepDoesNothingExtra() {
        service.updateCommercialProfile(COMMERCIAL_ID, unchangedRequest());

        verifyNoInteractions(contractRepository, eventPublisher);
        verify(commercialDetailsRepository).save(commercial);
    }

    @Test
    @DisplayName("valores guardados con espacios sobrantes no cuentan como cambio")
    void storedWhitespaceIsNotAChange() {
        onboarding.setLegalRepFirstName("Ana ");
        commercial.setLegalRepDocNumber(" 111");

        service.updateCommercialProfile(COMMERCIAL_ID, unchangedRequest());

        verifyNoInteractions(contractRepository, eventPublisher);
    }

    @Test
    @DisplayName("otra persona: guarda sus datos y audita anterior/nuevo")
    void changingLegalRepUpdatesAndAudits() {
        when(contractRepository.existsByCommercial_IdAndStatusIn(eq(COMMERCIAL_ID), any())).thenReturn(false);

        service.updateCommercialProfile(COMMERCIAL_ID, newPersonRequest());

        assertThat(onboarding.getLegalRepFirstName()).isEqualTo("Luis");
        assertThat(onboarding.getLegalRepLastName()).isEqualTo("Gómez");
        assertThat(commercial.getLegalRepDocType()).isEqualTo(DocumentType.CE);
        assertThat(commercial.getLegalRepDocNumber()).isEqualTo("222");

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("LEGAL_REPRESENTATIVE_CHANGED");
        assertThat(audit.getValue().getUserId()).isEqualTo(COMMERCIAL_ID);
        assertThat(audit.getValue().getAdditionalData())
                .containsEntry("previousFullName", "Ana Pérez")
                .containsEntry("previousDocType", "CC")
                .containsEntry("previousDocNumber", "111")
                .containsEntry("newFullName", "Luis Gómez")
                .containsEntry("newDocType", "CE")
                .containsEntry("newDocNumber", "222")
                .containsEntry("identityChanged", true);
    }

    @Test
    @DisplayName("con un contrato en revisión o pendiente de firma se rechaza y no se toca nada")
    void rejectedWhileContractInFlight() {
        when(contractRepository.existsByCommercial_IdAndStatusIn(eq(COMMERCIAL_ID), any())).thenReturn(true);

        assertThatThrownBy(() -> service.updateCommercialProfile(COMMERCIAL_ID, newPersonRequest()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("contrato");

        verifyNoInteractions(eventPublisher);
        verify(commercialDetailsRepository, never()).save(any());
        assertThat(onboarding.getLegalRepFirstName()).isEqualTo("Ana");
        assertThat(commercial.getLegalRepDocNumber()).isEqualTo("111");
    }

    @Test
    @DisplayName("solo cambia la declaración PEP: se aplica y se audita como cambio que no es de identidad")
    void pepOnlyChangeIsAudited() {
        when(contractRepository.existsByCommercial_IdAndStatusIn(eq(COMMERCIAL_ID), any())).thenReturn(false);
        CommercialUpdateProfileRequestDTO dto = unchangedRequest();
        dto.setLegalRepPepDeclaration(true);

        service.updateCommercialProfile(COMMERCIAL_ID, dto);

        assertThat(commercial.isPep()).isTrue();

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(audit.capture());
        assertThat(audit.getValue().getAdditionalData())
                .containsEntry("identityChanged", false)
                .containsEntry("previousPep", false)
                .containsEntry("newPep", true);
    }
}
