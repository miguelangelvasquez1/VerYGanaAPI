package com.verygana2.services.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.tax.TaxProfileDTOs.Establishment;
import com.verygana2.dtos.tax.TaxRequestDTOs.CloseEstablishmentRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.EstablishmentRequest;
import com.verygana2.event.TaxProfileChangedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.tax.EstablishmentRelation;
import com.verygana2.models.enums.tax.EstablishmentStatus;
import com.verygana2.models.tax.CommercialEstablishment;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.tax.CommercialEstablishmentRepository;
import com.verygana2.services.LocationService;

/** Establecimientos (4.7 f): el total se explica con sedes físicas, sin duplicados. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialEstablishmentServiceImpl")
class CommercialEstablishmentServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;

    @Mock private CommercialEstablishmentRepository establishmentRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private LocationService locationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CommercialEstablishmentServiceImpl service;

    private Municipality armenia;

    @BeforeEach
    void setUp() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        armenia = new Municipality();
        armenia.setCode("63001");
        armenia.setName("Armenia");
        lenient().when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
        lenient().when(locationService.getMunicipalityEntityByCode("63001")).thenReturn(armenia);
        lenient().when(establishmentRepository.save(any(CommercialEstablishment.class))).thenAnswer(inv -> {
            CommercialEstablishment e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
    }

    private EstablishmentRequest request(String address) {
        EstablishmentRequest r = new EstablishmentRequest();
        r.setName("Sede centro");
        r.setAddress(address);
        r.setMunicipalityCode("63001");
        r.setRelation(EstablishmentRelation.LEASED);
        return r;
    }

    @Test
    @DisplayName("registra una sede física y avisa el cambio de establecimientos")
    void createsPhysicalEstablishment() {
        Establishment created = service.create(COMMERCIAL_ID, request("Cra. 14 #20-30 Local 2"));

        assertThat(created.status()).isEqualTo(EstablishmentStatus.ACTIVE);
        assertThat(created.municipalityName()).isEqualTo("Armenia");
        verify(eventPublisher).publishEvent(any(TaxProfileChangedEvent.class));
    }

    @Test
    @DisplayName("un sitio web, correo o red social no es un establecimiento")
    void digitalChannelIsRejected() {
        for (String channel : new String[] { "https://mitienda.com", "www.mitienda.co", "ventas@mitienda.com", "@mitienda" }) {
            assertThatThrownBy(() -> service.create(COMMERCIAL_ID, request(channel)))
                    .as(channel)
                    .isInstanceOf(InvalidRequestException.class);
        }
        verify(establishmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("la misma sede escrita distinto no se cuenta dos veces")
    void duplicateAddressIsRejected() {
        when(establishmentRepository.existsByCommercial_IdAndStatusAndAddressNormalizedAndMunicipality_Code(
                eq(COMMERCIAL_ID), eq(EstablishmentStatus.ACTIVE), eq("cra 14 20 30 local 2"), eq("63001")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(COMMERCIAL_ID, request("CRA 14 # 20 - 30, local 2")))
                .isInstanceOf(InvalidRequestException.class);
        verify(establishmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("normaliza tildes, mayúsculas y signos de la dirección")
    void normalizesAddress() {
        assertThat(CommercialEstablishmentServiceImpl.normalizeAddress("Cálle 5 Nº 10-20, Ofíc. 3"))
                .isEqualTo("calle 5 n 10 20 ofic 3");
    }

    @Test
    @DisplayName("cerrar conserva el registro con su motivo; no se borra")
    void closeKeepsRecord() {
        CommercialEstablishment existing = new CommercialEstablishment();
        existing.setId(1L);
        existing.setMunicipality(armenia);
        existing.setStatus(EstablishmentStatus.ACTIVE);
        when(establishmentRepository.findByIdAndCommercial_Id(1L, COMMERCIAL_ID)).thenReturn(Optional.of(existing));
        CloseEstablishmentRequest close = new CloseEstablishmentRequest();
        close.setReason("Se trasladó a otra sede");

        Establishment closed = service.close(COMMERCIAL_ID, 1L, close);

        assertThat(closed.status()).isEqualTo(EstablishmentStatus.CLOSED);
        assertThat(closed.closedReason()).isEqualTo("Se trasladó a otra sede");
        verify(establishmentRepository, never()).delete(any());
        verify(eventPublisher).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    @DisplayName("un establecimiento de otro empresario no se puede cerrar")
    void cannotCloseOthers() {
        when(establishmentRepository.findByIdAndCommercial_Id(anyLong(), eq(COMMERCIAL_ID))).thenReturn(Optional.empty());
        CloseEstablishmentRequest close = new CloseEstablishmentRequest();
        close.setReason("x");

        assertThatThrownBy(() -> service.close(COMMERCIAL_ID, 99L, close))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
        verify(locationService, never()).getMunicipalityEntityByCode(anyString());
    }
}
