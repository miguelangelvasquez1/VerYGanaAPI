package com.verygana2.services.commercial;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.commercial.CommercialPageVisitRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialReportServiceImpl")
class CommercialReportServiceImplTest {

    private static final Long ID = 1L;
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final ZonedDateTime FROM = LocalDate.of(2026, 1, 1).atStartOfDay(ZONE);
    private static final ZonedDateTime TO = LocalDate.of(2026, 1, 8).atStartOfDay(ZONE);

    @Mock private AdRepository adRepository;
    @Mock private AdLikeRepository adLikeRepository;
    @Mock private CommercialPageVisitRepository pageVisitRepository;
    @Mock private SurveyRepository surveyRepository;
    @Mock private SurveySessionRepository surveySessionRepository;
    @Mock private SurveyRewardRepository surveyRewardRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private GameSessionRepository gameSessionRepository;

    @InjectMocks private CommercialReportServiceImpl service;

    @Test
    @DisplayName("getAdsReport: agrega estados, presupuestos y avance — sin datos de CommercialPageVisit")
    void adsReport_aggregates() {
        Ad active = Ad.builder().id(1L).title("A1").status(AdStatus.ACTIVE)
                .currentLikes(50).maxLikes(100).rewardPerLike(200L)
                .createdAt(FROM).build();
        Ad paused = Ad.builder().id(2L).title("A2").status(AdStatus.PAUSED)
                .currentLikes(10).maxLikes(20).rewardPerLike(100L)
                .createdAt(FROM).build();

        when(adRepository.findAllByCommercialId(ID)).thenReturn(List.of(active, paused));
        when(adLikeRepository.countByAdInRange(ID, FROM, TO))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, 30L }));
        when(adLikeRepository.countByCommercialIdAndCreatedAtRange(ID, FROM, TO)).thenReturn(30L);
        when(adLikeRepository.sumRewardInRange(ID, FROM, TO)).thenReturn(6000L);
        when(adLikeRepository.countByDayInRange(ID, FROM, TO)).thenReturn(List.of());

        AdsReportResponseDTO report = service.getAdsReport(ID, FROM, TO);
        AdsReportResponseDTO.Summary s = report.summary();

        assertThat(s.totalAds()).isEqualTo(2);
        assertThat(s.activeAds()).isEqualTo(1);
        assertThat(s.pausedAds()).isEqualTo(1);
        assertThat(s.interactions()).isEqualTo(30);
        assertThat(s.lifetimeInteractions()).isEqualTo(60);
        assertThat(s.totalBudgetCents()).isEqualTo(22_000);
        assertThat(s.spentBudgetCents()).isEqualTo(11_000);
        assertThat(s.remainingBudgetCents()).isEqualTo(11_000);
        assertThat(s.avgCompletionRatePct()).isEqualTo(50.0);

        assertThat(report.interactionsByDay()).hasSize(7);
        assertThat(report.interactionsByDay()).allSatisfy(p -> assertThat(p.count()).isZero());
        assertThat(report.perAd().get(0).adId()).isEqualTo(1L);
        assertThat(report.perAd().get(0).interactions()).isEqualTo(30);

        // Este reporte también lo ve un comercial Estándar: no debe tocar
        // CommercialPageVisit, que es la métrica exclusiva Premium ("Remisión").
        verifyNoInteractions(pageVisitRepository);
    }

    @Test
    @DisplayName("getAdsReport: avgCompletionRatePct null cuando no hay anuncios")
    void adsReport_completionNullWhenNoAds() {
        when(adRepository.findAllByCommercialId(ID)).thenReturn(List.of());
        when(adLikeRepository.countByAdInRange(ID, FROM, TO)).thenReturn(List.of());
        when(adLikeRepository.countByCommercialIdAndCreatedAtRange(ID, FROM, TO)).thenReturn(0L);
        when(adLikeRepository.sumRewardInRange(ID, FROM, TO)).thenReturn(0L);
        when(adLikeRepository.countByDayInRange(ID, FROM, TO)).thenReturn(List.of());

        AdsReportResponseDTO.Summary s = service.getAdsReport(ID, FROM, TO).summary();

        assertThat(s.avgCompletionRatePct()).isNull();
        verifyNoInteractions(pageVisitRepository);
    }

    @Test
    @DisplayName("getPageVisitsReport: delta vs. período anterior y conversión desde anuncios")
    void pageVisitsReport_deltaAndConversion() {
        ZonedDateTime prevFrom = FROM.minus(Duration.between(FROM, TO));

        when(pageVisitRepository.countInRange(ID, FROM, TO)).thenReturn(20L);
        when(pageVisitRepository.countUniqueVisitorsInRange(ID, FROM, TO)).thenReturn(12L);
        when(pageVisitRepository.countLifetime(ID)).thenReturn(100L);
        when(pageVisitRepository.countInRange(ID, prevFrom, FROM)).thenReturn(10L);
        when(adLikeRepository.countDistinctConsumersInRange(ID, FROM, TO)).thenReturn(8L);
        when(pageVisitRepository.countConvertedVisitorsInRange(ID, FROM, TO)).thenReturn(4L);
        when(pageVisitRepository.visitsByAd(ID, FROM, TO))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, "A1", 8L, 5L }));
        when(pageVisitRepository.findTop20ByCommercialIdOrderByCreatedAtDesc(ID)).thenReturn(List.of());
        when(pageVisitRepository.visitsByDay(ID, FROM, TO)).thenReturn(List.of());

        PageVisitsReportResponseDTO.Summary s = service.getPageVisitsReport(ID, FROM, TO).summary();

        assertThat(s.totalVisits()).isEqualTo(20);
        assertThat(s.uniqueVisitors()).isEqualTo(12);
        assertThat(s.lifetimeVisits()).isEqualTo(100);
        assertThat(s.previousPeriodVisits()).isEqualTo(10);
        assertThat(s.deltaPct()).isEqualTo(100.0);
        assertThat(s.conversionRatePct()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getPageVisitsReport: la conversión nunca supera 100% aunque haya muchas más visitas (eventos) que likers (personas)")
    void pageVisitsReport_conversionNeverExceeds100() {
        // Un mismo puñado de consumers puede generar muchas más visitas que likes (el like es
        // único por consumer/anuncio, la visita no) — regresión del bug donde conversionRatePct
        // comparaba visitas vs. likes en bruto y podía devolver, p.ej., 335%.
        when(pageVisitRepository.countInRange(ID, FROM, TO)).thenReturn(200L);
        when(pageVisitRepository.countUniqueVisitorsInRange(ID, FROM, TO)).thenReturn(6L);
        when(pageVisitRepository.countLifetime(ID)).thenReturn(200L);
        when(pageVisitRepository.countInRange(ID, FROM.minus(Duration.between(FROM, TO)), FROM)).thenReturn(0L);
        when(adLikeRepository.countDistinctConsumersInRange(ID, FROM, TO)).thenReturn(6L);
        when(pageVisitRepository.countConvertedVisitorsInRange(ID, FROM, TO)).thenReturn(6L);
        when(pageVisitRepository.visitsByAd(ID, FROM, TO)).thenReturn(List.of());
        when(pageVisitRepository.findTop20ByCommercialIdOrderByCreatedAtDesc(ID)).thenReturn(List.of());
        when(pageVisitRepository.visitsByDay(ID, FROM, TO)).thenReturn(List.of());

        PageVisitsReportResponseDTO.Summary s = service.getPageVisitsReport(ID, FROM, TO).summary();

        assertThat(s.totalVisits()).isEqualTo(200);
        assertThat(s.conversionRatePct()).isEqualTo(100.0);
    }
}
