package com.verygana2.services.commercial;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.dtos.commercial.report.DailyCountDTO;
import com.verygana2.dtos.commercial.report.GamesReportResponseDTO;
import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.dtos.commercial.report.ReportPeriodDTO;
import com.verygana2.dtos.commercial.report.SurveysReportResponseDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.commercial.CommercialPageVisit;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveySession.SessionStatus;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.commercial.CommercialPageVisitRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/**
 * Ver {@link CommercialReportService}. Todo se calcula en tiempo real; los montos
 * van en centavos y las series diarias vienen rellenas con ceros. El gating por
 * plan se aplica vía {@code @RequirePlanCapability} (el parámetro {@code commercialId}
 * es el que lee {@code PlanGuardAspect}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommercialReportServiceImpl implements CommercialReportService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final AdRepository adRepository;
    private final AdLikeRepository adLikeRepository;
    private final CommercialPageVisitRepository pageVisitRepository;
    private final SurveyRepository surveyRepository;
    private final SurveySessionRepository surveySessionRepository;
    private final SurveyRewardRepository surveyRewardRepository;
    private final CampaignRepository campaignRepository;
    private final GameSessionRepository gameSessionRepository;

    // ══════════════════════════ ANUNCIOS ══════════════════════════

    @Override
    @RequirePlanCapability(value = Capability.CAN_VIEW_PERFORMANCE_METRICS)
    public AdsReportResponseDTO getAdsReport(Long commercialId, ZonedDateTime from, ZonedDateTime to) {
        // OJO: este reporte lo ven también los comerciales Estándar (CAN_VIEW_PERFORMANCE_METRICS).
        // No debe incluir nada derivado de CommercialPageVisit — esa métrica ("Remisión") es
        // exclusiva Premium (CAN_VIEW_PAGE_VISIT_METRICS) y se sirve solo por getPageVisitsReport.
        List<Ad> ads = adRepository.findAllByCommercialId(commercialId);

        Map<Long, Long> likesByAd = toLongMap(adLikeRepository.countByAdInRange(commercialId, from, to));

        long interactions = adLikeRepository.countByCommercialIdAndCreatedAtRange(commercialId, from, to);
        long rewardPaidCents = adLikeRepository.sumRewardInRange(commercialId, from, to);

        long totalBudget = 0L;
        long spentBudget = 0L;
        long lifetimeInteractions = 0L;
        double completionSum = 0d;
        int completionCount = 0;
        Map<AdStatus, Long> byStatus = new EnumMap<>(AdStatus.class);

        List<AdsReportResponseDTO.AdRow> perAd = new ArrayList<>();
        for (Ad ad : ads) {
            byStatus.merge(ad.getStatus(), 1L, Long::sum);
            long lifetimeLikes = nz(ad.getCurrentLikes());
            int maxLikes = (int) nz(ad.getMaxLikes());
            long adBudget = nz(ad.getTotalBudget());
            long adSpent = nz(ad.getSpentBudget());
            totalBudget += adBudget;
            spentBudget += adSpent;
            lifetimeInteractions += lifetimeLikes;

            Double completionPct = maxLikes > 0 ? round2(lifetimeLikes * 100d / maxLikes) : null;
            if (completionPct != null) {
                completionSum += completionPct;
                completionCount++;
            }

            perAd.add(new AdsReportResponseDTO.AdRow(
                    ad.getId(), ad.getTitle(), ad.getStatus().name(),
                    likesByAd.getOrDefault(ad.getId(), 0L),
                    lifetimeLikes, maxLikes, completionPct,
                    nz(ad.getRewardPerLike()), adBudget, adSpent,
                    ad.getCreatedAt(), ad.getStartDate(), ad.getEndDate()));
        }
        perAd.sort((a, b) -> Long.compare(b.interactions(), a.interactions()));

        AdsReportResponseDTO.Summary summary = new AdsReportResponseDTO.Summary(
                ads.size(),
                byStatus.getOrDefault(AdStatus.ACTIVE, 0L),
                byStatus.getOrDefault(AdStatus.PAUSED, 0L),
                byStatus.getOrDefault(AdStatus.COMPLETED, 0L),
                byStatus.getOrDefault(AdStatus.PENDING, 0L),
                byStatus.getOrDefault(AdStatus.REJECTED, 0L),
                interactions,
                lifetimeInteractions,
                rewardPaidCents,
                totalBudget,
                spentBudget,
                Math.max(0L, totalBudget - spentBudget),
                completionCount > 0 ? round2(completionSum / completionCount) : null);

        return AdsReportResponseDTO.builder()
                .period(periodOf(from, to))
                .summary(summary)
                .perAd(perAd)
                .interactionsByDay(buildDailySeries(adLikeRepository.countByDayInRange(commercialId, from, to), from, to))
                .build();
    }

    // ══════════════════════════ ENCUESTAS ══════════════════════════

    @Override
    @RequirePlanCapability(value = Capability.CAN_VIEW_PERFORMANCE_METRICS)
    public SurveysReportResponseDTO getSurveysReport(Long commercialId, ZonedDateTime from, ZonedDateTime to) {
        List<Survey> surveys = surveyRepository.findAllByCreatorId(commercialId);
        Map<Long, Integer> questionCounts = toIntMap(surveyRepository.countQuestionsByCreator(commercialId));

        Map<SessionStatus, Long> sessionsByStatus = toStatusMap(
                surveySessionRepository.countByStatusInRange(commercialId, from, to));
        Map<Long, Map<SessionStatus, Long>> perSurveyStatus = toPerSurveyStatusMap(
                surveySessionRepository.countBySurveyAndStatusInRange(commercialId, from, to));

        long startedSessions = sessionsByStatus.values().stream().mapToLong(Long::longValue).sum();
        long completedSessions = sessionsByStatus.getOrDefault(SessionStatus.COMPLETED, 0L);
        long abandonedSessions = sessionsByStatus.getOrDefault(SessionStatus.ABANDONED, 0L);
        long expiredSessions = sessionsByStatus.getOrDefault(SessionStatus.EXPIRED, 0L);

        List<DailyCountDTO> responsesByDay = buildDailySeries(
                surveySessionRepository.countCompletedByDayInRange(commercialId, from, to), from, to);
        long responsesInPeriod = responsesByDay.stream().mapToLong(DailyCountDTO::count).sum();

        Map<Survey.SurveyStatus, Long> byStatus = new EnumMap<>(Survey.SurveyStatus.class);
        long totalResponses = 0L;
        long activeSurveyResponses = 0L;
        long activeSurveys = 0L;
        List<SurveysReportResponseDTO.SurveyRow> perSurvey = new ArrayList<>();
        for (Survey s : surveys) {
            byStatus.merge(s.getStatus(), 1L, Long::sum);
            int responseCount = (int) nz(s.getResponseCount());
            totalResponses += responseCount;
            if (s.getStatus() == Survey.SurveyStatus.ACTIVE) {
                activeSurveys++;
                activeSurveyResponses += responseCount;
            }

            Map<SessionStatus, Long> st = perSurveyStatus.getOrDefault(s.getId(), Map.of());
            long sStarted = st.values().stream().mapToLong(Long::longValue).sum();
            long sCompleted = st.getOrDefault(SessionStatus.COMPLETED, 0L);
            long sAbandoned = st.getOrDefault(SessionStatus.ABANDONED, 0L);

            Integer maxResponses = s.getMaxResponses();
            Double fillRate = (maxResponses != null && maxResponses > 0)
                    ? round2(responseCount * 100d / maxResponses) : null;

            perSurvey.add(new SurveysReportResponseDTO.SurveyRow(
                    s.getId(), s.getTitle(), s.getStatus().name(),
                    responseCount, maxResponses, fillRate,
                    sStarted, sCompleted, sAbandoned,
                    sStarted > 0 ? round2(sCompleted * 100d / sStarted) : null,
                    questionCounts.getOrDefault(s.getId(), 0),
                    s.getCreatedAt(), s.getStartsAt(), s.getEndsAt()));
        }
        perSurvey.sort((a, b) -> Long.compare(b.completedSessions(), a.completedSessions()));

        SurveysReportResponseDTO.Summary summary = new SurveysReportResponseDTO.Summary(
                surveys.size(),
                byStatus.getOrDefault(Survey.SurveyStatus.DRAFT, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.PENDING_REVIEW, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.APPROVED, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.ACTIVE, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.PAUSED, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.COMPLETED, 0L),
                byStatus.getOrDefault(Survey.SurveyStatus.REJECTED, 0L),
                totalResponses,
                startedSessions, completedSessions, abandonedSessions, expiredSessions,
                startedSessions > 0 ? round2(completedSessions * 100d / startedSessions) : null,
                responsesInPeriod,
                activeSurveys > 0 ? round2((double) activeSurveyResponses / activeSurveys) : null,
                surveyRewardRepository.sumProcessedByCommercialInRange(commercialId, from, to));

        return SurveysReportResponseDTO.builder()
                .period(periodOf(from, to))
                .summary(summary)
                .perSurvey(perSurvey)
                .responsesByDay(responsesByDay)
                .build();
    }

    // ══════════════════════════ JUEGOS / CAMPAÑAS ══════════════════════════

    @Override
    @RequirePlanCapability(value = Capability.CAN_VIEW_PERFORMANCE_METRICS)
    public GamesReportResponseDTO getGamesReport(Long commercialId, ZonedDateTime from, ZonedDateTime to) {
        List<Campaign> campaigns = campaignRepository.findByCommercialId(commercialId);

        long[] agg = firstRowOrZeros(gameSessionRepository.aggregateByCommercialInRange(commercialId, from, to), 5);
        long sessionsPlayed = agg[0];
        long completedSessions = agg[1];
        long uniquePlayers = agg[2];
        long totalPlayTimeSeconds = agg[3];
        long rewardsPaidCents = agg[4];

        Map<Long, long[]> perCampaignAgg = new HashMap<>();
        for (Object[] row : gameSessionRepository.aggregateByCampaignInRange(commercialId, from, to)) {
            perCampaignAgg.put(((Number) row[0]).longValue(), new long[] {
                    ((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue() });
        }

        long totalBudget = 0L;
        long spentBudget = 0L;
        long lifetimeSessionsPlayed = 0L;
        long lifetimeCompletedSessions = 0L;
        Map<CampaignStatus, Long> byStatus = new EnumMap<>(CampaignStatus.class);
        List<GamesReportResponseDTO.CampaignRow> perCampaign = new ArrayList<>();

        for (Campaign c : campaigns) {
            byStatus.merge(c.getStatus(), 1L, Long::sum);
            totalBudget += nz(c.getBudgetCents());
            spentBudget += nz(c.getSpentCents());
            lifetimeSessionsPlayed += nz(c.getSessionsPlayed());
            lifetimeCompletedSessions += nz(c.getCompletedSessions());

            long[] a = perCampaignAgg.getOrDefault(c.getId(), new long[4]);
            long cPlayed = a[0];
            long cCompleted = a[1];
            perCampaign.add(new GamesReportResponseDTO.CampaignRow(
                    c.getId(),
                    c.getGame() != null ? c.getGame().getTitle() : "Campaña #" + c.getId(),
                    c.getStatus().name(),
                    cPlayed, cCompleted, a[2],
                    cPlayed > 0 ? round2(cCompleted * 100d / cPlayed) : null,
                    a[3],
                    nz(c.getBudgetCents()), nz(c.getSpentCents()),
                    c.getStartDate(), c.getEndDate()));
        }
        perCampaign.sort((x, y) -> Long.compare(y.sessionsPlayed(), x.sessionsPlayed()));

        GamesReportResponseDTO.Summary summary = new GamesReportResponseDTO.Summary(
                campaigns.size(),
                byStatus.getOrDefault(CampaignStatus.DRAFT, 0L),
                byStatus.getOrDefault(CampaignStatus.ACTIVE, 0L),
                byStatus.getOrDefault(CampaignStatus.PAUSED, 0L),
                byStatus.getOrDefault(CampaignStatus.COMPLETED, 0L),
                byStatus.getOrDefault(CampaignStatus.CANCELLED, 0L),
                sessionsPlayed, completedSessions, uniquePlayers,
                sessionsPlayed > 0 ? round2(completedSessions * 100d / sessionsPlayed) : null,
                totalPlayTimeSeconds,
                sessionsPlayed > 0 ? round2((double) totalPlayTimeSeconds / sessionsPlayed) : null,
                totalBudget, spentBudget, rewardsPaidCents,
                lifetimeSessionsPlayed, lifetimeCompletedSessions);

        return GamesReportResponseDTO.builder()
                .period(periodOf(from, to))
                .summary(summary)
                .perCampaign(perCampaign)
                .playsByDay(buildDailySeries(gameSessionRepository.countByDayInRange(commercialId, from, to), from, to))
                .build();
    }

    // ══════════════════════════ REMISIÓN / VISITAS ══════════════════════════

    @Override
    @RequirePlanCapability(value = Capability.CAN_VIEW_PAGE_VISIT_METRICS)
    public PageVisitsReportResponseDTO getPageVisitsReport(Long commercialId, ZonedDateTime from, ZonedDateTime to) {
        long totalVisits = pageVisitRepository.countInRange(commercialId, from, to);
        long uniqueVisitors = pageVisitRepository.countUniqueVisitorsInRange(commercialId, from, to);
        long lifetimeVisits = pageVisitRepository.countLifetime(commercialId);

        Duration window = Duration.between(from, to);
        ZonedDateTime prevFrom = from.minus(window);
        long previousPeriodVisits = pageVisitRepository.countInRange(commercialId, prevFrom, from);

        // Tasa de conversión = % de consumers que dieron like a un anuncio y ADEMÁS visitaron la
        // página, sobre el total de consumers que dieron like. Es una intersección de personas
        // (no una comparación de conteos de eventos independientes), así que queda acotada en
        // [0, 100] — comparar visitas vs. likes en bruto no lo está: un mismo consumer puede
        // visitar varias veces sin volver a dar like (el like es único por consumer/anuncio).
        long likedConsumers = adLikeRepository.countDistinctConsumersInRange(commercialId, from, to);
        long convertedConsumers = pageVisitRepository.countConvertedVisitorsInRange(commercialId, from, to);

        List<PageVisitsReportResponseDTO.AdVisits> visitsByAd = new ArrayList<>();
        for (Object[] row : pageVisitRepository.visitsByAd(commercialId, from, to)) {
            visitsByAd.add(new PageVisitsReportResponseDTO.AdVisits(
                    row[0] != null ? ((Number) row[0]).longValue() : null,
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()));
        }

        List<PageVisitsReportResponseDTO.RecentVisit> recentVisits = new ArrayList<>();
        for (CommercialPageVisit v : pageVisitRepository.findTop20ByCommercialIdOrderByCreatedAtDesc(commercialId)) {
            recentVisits.add(new PageVisitsReportResponseDTO.RecentVisit(
                    v.getAd() != null ? v.getAd().getId() : null,
                    v.getAd() != null ? v.getAd().getTitle() : null,
                    v.getCreatedAt()));
        }

        PageVisitsReportResponseDTO.Summary summary = new PageVisitsReportResponseDTO.Summary(
                totalVisits, uniqueVisitors, lifetimeVisits, previousPeriodVisits,
                previousPeriodVisits > 0
                        ? round2((totalVisits - previousPeriodVisits) * 100d / previousPeriodVisits) : null,
                likedConsumers > 0 ? round2(convertedConsumers * 100d / likedConsumers) : null);

        return PageVisitsReportResponseDTO.builder()
                .period(periodOf(from, to))
                .summary(summary)
                .visitsByDay(buildDailySeries(pageVisitRepository.visitsByDay(commercialId, from, to), from, to))
                .visitsByAd(visitsByAd)
                .recentVisits(recentVisits)
                .build();
    }

    // ══════════════════════════ Helpers ══════════════════════════

    private static ReportPeriodDTO periodOf(ZonedDateTime from, ZonedDateTime to) {
        return new ReportPeriodDTO(
                from.withZoneSameInstant(ZONE).toLocalDate(),
                to.withZoneSameInstant(ZONE).minusNanos(1).toLocalDate());
    }

    /** Convierte filas {@code [day, count]} en una serie continua diaria rellena con ceros. */
    private static List<DailyCountDTO> buildDailySeries(List<Object[]> rows, ZonedDateTime from, ZonedDateTime to) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            byDay.merge(toLocalDate(row[0]), ((Number) row[1]).longValue(), Long::sum);
        }
        LocalDate start = from.withZoneSameInstant(ZONE).toLocalDate();
        LocalDate lastExclusive = to.withZoneSameInstant(ZONE).toLocalDate();
        List<DailyCountDTO> series = new ArrayList<>();
        for (LocalDate d = start; d.isBefore(lastExclusive); d = d.plusDays(1)) {
            series.add(new DailyCountDTO(d, byDay.getOrDefault(d, 0L)));
        }
        return series;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp t) return t.toInstant().atZone(ZONE).toLocalDate();
        if (value instanceof java.util.Date d) return d.toInstant().atZone(ZONE).toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    /** [id, count] → Map<id, count>. */
    private static Map<Long, Long> toLongMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static Map<Long, Integer> toIntMap(List<Object[]> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return map;
    }

    private static Map<SessionStatus, Long> toStatusMap(List<Object[]> rows) {
        Map<SessionStatus, Long> map = new EnumMap<>(SessionStatus.class);
        for (Object[] row : rows) {
            map.merge((SessionStatus) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        return map;
    }

    private static Map<Long, Map<SessionStatus, Long>> toPerSurveyStatusMap(List<Object[]> rows) {
        Map<Long, Map<SessionStatus, Long>> map = new HashMap<>();
        for (Object[] row : rows) {
            Long surveyId = ((Number) row[0]).longValue();
            map.computeIfAbsent(surveyId, k -> new EnumMap<>(SessionStatus.class))
                    .merge((SessionStatus) row[1], ((Number) row[2]).longValue(), Long::sum);
        }
        return map;
    }

    private static long[] firstRowOrZeros(List<Object[]> rows, int size) {
        long[] out = new long[size];
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return out;
        }
        Object[] row = rows.get(0);
        for (int i = 0; i < size && i < row.length; i++) {
            out[i] = row[i] != null ? ((Number) row[i]).longValue() : 0L;
        }
        return out;
    }

    private static Double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static long nz(Integer v) {
        return v != null ? v : 0L;
    }
}
