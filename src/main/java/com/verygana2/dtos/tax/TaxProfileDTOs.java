package com.verygana2.dtos.tax;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.verygana2.dtos.declarations.DeclarationDTOs.Manifestation;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.enums.tax.EstablishmentRelation;
import com.verygana2.models.enums.tax.EstablishmentStatus;
import com.verygana2.models.enums.tax.IncomeConcept;
import com.verygana2.models.enums.tax.IncomeScope;
import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxCatalogType;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;

/** Respuestas de la ficha tributaria, establecimientos y catálogo. */
public final class TaxProfileDTOs {

    private TaxProfileDTOs() {
    }

    /** Ficha vigente: la última versión enviada y el estado de revisión de cada sección. */
    public record TaxProfileResponse(
            TaxProfileStatus status,
            int currentVersion,
            ZonedDateTime updatedAt,
            TaxProfileVersionDetail version,
            List<SectionReview> sectionReviews,
            List<Establishment> activeEstablishments,
            /**
             * Compromiso de formalización recogido en la Hoja Final del contrato, por
             * referencia. La ficha nunca pide aceptarlo otra vez. Null si aún no hay registro.
             */
            Manifestation formalizationCommitment) {
    }

    /** Vista de compliance: la ficha más los datos del empresario para contrastar. */
    public record ComplianceTaxProfileResponse(
            UUID commercialPublicId,
            String companyName,
            String nit,
            /** Vocación Empresarial: se muestra para contrastar, la ficha nunca la cambia. */
            CommercialActivityType commercialActivityType,
            TaxProfileResponse profile,
            List<VersionSummary> versions,
            List<Establishment> establishmentHistory) {
    }

    public record TaxProfileSummary(
            UUID commercialPublicId,
            String companyName,
            TaxProfileStatus status,
            int currentVersion,
            ZonedDateTime updatedAt) {
    }

    public record VersionSummary(int versionNumber, ZonedDateTime submittedAt) {
    }

    public record TaxProfileVersionDetail(
            int versionNumber,
            ZonedDateTime submittedAt,
            Long rutDocumentId,
            List<String> rutResponsibilityCodes,
            String regimeCode,
            List<Activity> activities,
            String vatConditionCode,
            String incConditionCode,
            String taxConditionScope,
            boolean invoicingObligated,
            String invoicingMethodCode,
            String invoicingNotes,
            boolean withholdingAgent,
            String withholdingNotes,
            List<IncomeReport> incomeReports,
            int establishmentsCount,
            String currentCapacity,
            String growthScenario,
            String growthMeasures,
            boolean hasAccountingAdvisor,
            String advisorScope) {
    }

    public record Activity(String ciiuCode, String description, boolean main) {
    }

    public record IncomeReport(
            IncomeConcept concept,
            LocalDate periodStart,
            LocalDate periodEnd,
            long amountCents,
            String currency,
            IncomeScope scope,
            Long supportDocumentId,
            String notes) {
    }

    /** Revisión vigente de una sección. Sin fila = sección todavía sin revisar. */
    public record SectionReview(
            TaxProfileSection section,
            SectionReviewResult result,
            String notes,
            ZonedDateTime reviewedAt) {
    }

    public record Establishment(
            Long id,
            String name,
            String address,
            String municipalityCode,
            String municipalityName,
            EstablishmentRelation relation,
            EstablishmentStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime closedAt,
            String closedReason) {
    }

    public record CatalogEntry(
            Long id,
            TaxCatalogType catalogType,
            String code,
            String label,
            String legalBasis,
            LocalDate validFrom,
            LocalDate validTo,
            boolean active) {
    }
}
