package com.verygana2.services.tax;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.verygana2.dtos.tax.TaxProfileDTOs.Activity;
import com.verygana2.dtos.tax.TaxProfileDTOs.Establishment;
import com.verygana2.dtos.tax.TaxProfileDTOs.IncomeReport;
import com.verygana2.dtos.tax.TaxProfileDTOs.SectionReview;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileVersionDetail;
import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.models.tax.CommercialEstablishment;
import com.verygana2.models.tax.TaxProfileSectionReview;
import com.verygana2.models.tax.TaxProfileVersion;

/** Mapeo de entidades de la ficha a DTOs, y cálculo del estado a partir de las revisiones. */
final class TaxProfileViews {

    private TaxProfileViews() {
    }

    static Establishment establishment(CommercialEstablishment e) {
        return new Establishment(e.getId(), e.getName(), e.getAddress(),
                e.getMunicipality().getCode(), e.getMunicipality().getName(),
                e.getRelation(), e.getStatus(), e.getCreatedAt(), e.getClosedAt(), e.getClosedReason());
    }

    static TaxProfileVersionDetail version(TaxProfileVersion v) {
        List<Activity> activities = v.getActivities().stream()
                .map(a -> new Activity(a.getCiiuCode(), a.getDescription(), a.isMain())).toList();
        List<IncomeReport> income = v.getIncomeReports().stream()
                .map(r -> new IncomeReport(r.getConcept(), r.getPeriodStart(), r.getPeriodEnd(), r.getAmountCents(),
                        r.getCurrency(), r.getScope(),
                        r.getSupportDocument() != null ? r.getSupportDocument().getId() : null, r.getNotes()))
                .toList();
        return new TaxProfileVersionDetail(
                v.getVersionNumber(), v.getSubmittedAt(),
                v.getRutDocument() != null ? v.getRutDocument().getId() : null,
                List.copyOf(v.getRutResponsibilityCodes()), v.getRegimeCode(), activities,
                v.getVatConditionCode(), v.getIncConditionCode(), v.getTaxConditionScope(),
                v.isInvoicingObligated(), v.getInvoicingMethodCode(), v.getInvoicingNotes(),
                v.isWithholdingAgent(), v.getWithholdingNotes(), income, v.getEstablishmentsCount(),
                v.getCurrentCapacity(), v.getGrowthScenario(), v.getGrowthMeasures(),
                v.isHasAccountingAdvisor(), v.getAdvisorScope());
    }

    /** La revisión vigente de cada sección es la más reciente. Recibe las filas ordenadas, más reciente primero. */
    static Map<TaxProfileSection, TaxProfileSectionReview> latestBySection(List<TaxProfileSectionReview> newestFirst) {
        Map<TaxProfileSection, TaxProfileSectionReview> latest = new EnumMap<>(TaxProfileSection.class);
        for (TaxProfileSectionReview review : newestFirst) {
            latest.putIfAbsent(review.getSection(), review);
        }
        return latest;
    }

    static List<SectionReview> sectionReviews(Map<TaxProfileSection, TaxProfileSectionReview> latest) {
        return latest.values().stream()
                .map(r -> new SectionReview(r.getSection(), r.getResult(), r.getNotes(), r.getReviewedAt()))
                .toList();
    }

    /**
     * Alguna sección observada → OBSERVED. Todas revisadas sin observaciones → REVIEWED.
     * Si falta alguna, sigue SUBMITTED.
     */
    static TaxProfileStatus statusFrom(Map<TaxProfileSection, TaxProfileSectionReview> latest) {
        boolean observed = latest.values().stream().anyMatch(r -> r.getResult() == SectionReviewResult.OBSERVED);
        if (observed) {
            return TaxProfileStatus.OBSERVED;
        }
        return latest.size() == TaxProfileSection.values().length ? TaxProfileStatus.REVIEWED : TaxProfileStatus.SUBMITTED;
    }
}
