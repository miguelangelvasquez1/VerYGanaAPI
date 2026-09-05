package com.verygana2.services.commercial;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.commercial.report.RegisterPageVisitRequestDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.commercial.CommercialPageVisit;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.commercial.CommercialPageVisitRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialPageVisitServiceImpl")
class CommercialPageVisitServiceImplTest {

    private static final Long AD_ID = 7L;
    private static final Long CONSUMER_ID = 99L;

    @Mock private CommercialPageVisitRepository pageVisitRepository;
    @Mock private AdRepository adRepository;
    @Mock private ConsumerDetailsRepository consumerDetailsRepository;

    @InjectMocks private CommercialPageVisitServiceImpl service;

    private Ad ad;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dedupWindowMinutes", 30L);
        ad = Ad.builder()
                .id(AD_ID)
                .title("Anuncio de prueba")
                .targetUrl("https://empresa.example/landing")
                .commercial(new CommercialDetails())
                .build();
    }

    @Test
    @DisplayName("anuncio inexistente: lanza EntityNotFoundException")
    void adNotFound_throws() {
        when(adRepository.findById(AD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerVisit(CONSUMER_ID, new RegisterPageVisitRequestDTO(AD_ID, null)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(pageVisitRepository, never()).save(any());
    }

    @Test
    @DisplayName("dentro de la ventana de dedup: no persiste")
    void withinDedupWindow_skips() {
        when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));
        when(pageVisitRepository.existsByAdIdAndConsumerIdAndCreatedAtAfter(eq(AD_ID), eq(CONSUMER_ID), any()))
                .thenReturn(true);

        service.registerVisit(CONSUMER_ID, new RegisterPageVisitRequestDTO(AD_ID, null));

        verify(pageVisitRepository, never()).save(any());
    }

    @Test
    @DisplayName("fuera de la ventana: persiste con targetUrl del request")
    void outsideDedupWindow_persistsWithRequestUrl() {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setUserHash("hash-abc");
        when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));
        when(pageVisitRepository.existsByAdIdAndConsumerIdAndCreatedAtAfter(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(consumerDetailsRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));

        service.registerVisit(CONSUMER_ID, new RegisterPageVisitRequestDTO(AD_ID, "https://empresa.example/promo"));

        ArgumentCaptor<CommercialPageVisit> captor = ArgumentCaptor.forClass(CommercialPageVisit.class);
        verify(pageVisitRepository).save(captor.capture());
        CommercialPageVisit saved = captor.getValue();
        assertThat(saved.getAd()).isSameAs(ad);
        assertThat(saved.getCommercial()).isSameAs(ad.getCommercial());
        assertThat(saved.getTargetUrl()).isEqualTo("https://empresa.example/promo");
        assertThat(saved.getUserHash()).isEqualTo("hash-abc");
        assertThat(saved.getSource()).isEqualTo(CommercialPageVisit.PageVisitSource.AD);
    }

    @Test
    @DisplayName("sin targetUrl en el request: usa el del anuncio")
    void nullRequestUrl_fallsBackToAdUrl() {
        when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));
        when(pageVisitRepository.existsByAdIdAndConsumerIdAndCreatedAtAfter(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(consumerDetailsRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(new ConsumerDetails()));

        service.registerVisit(CONSUMER_ID, new RegisterPageVisitRequestDTO(AD_ID, "  "));

        ArgumentCaptor<CommercialPageVisit> captor = ArgumentCaptor.forClass(CommercialPageVisit.class);
        verify(pageVisitRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetUrl()).isEqualTo("https://empresa.example/landing");
    }
}
