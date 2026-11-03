package com.verygana2.dtos.inforequests;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.inforequests.AlertRuleType;
import com.verygana2.models.enums.inforequests.CommunicationChannel;
import com.verygana2.models.enums.inforequests.ContractualMeasure;
import com.verygana2.models.enums.inforequests.DeliveryStatus;
import com.verygana2.models.enums.inforequests.FindingClassification;
import com.verygana2.models.enums.inforequests.FindingSeverity;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.enums.inforequests.RequestItemType;
import com.verygana2.models.enums.inforequests.RequestReviewResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Requerimientos de actualización, hallazgos, incidencias y alertas (4.7 / 4.8). */
public final class InformationRequestDTOs {

    private InformationRequestDTOs() {
    }

    // ==================== RESPUESTAS ====================

    public record RequestView(
            UUID publicId,
            UUID commercialPublicId,
            String companyName,
            InformationRequestTrigger trigger,
            String ruleCode,
            Integer ruleVersion,
            String reason,
            String relationToOperation,
            InformationRequestStatus status,
            LocalDate dueDate,
            boolean issuedBySystem,
            ZonedDateTime issuedAt,
            ZonedDateTime closedAt,
            String closingNotes,
            List<ItemView> items,
            List<ResponseView> responses,
            List<ReviewView> reviews,
            /** Solo para compliance; vacío en la vista del empresario. */
            List<CommunicationView> communications) {
    }

    public record ItemView(
            RequestItemType itemType,
            CommercialDocumentType documentType,
            String fieldPath,
            Long establishmentId,
            String description,
            String acceptableResponse,
            Long linkedDocumentId) {
    }

    public record ResponseView(String message, List<Long> documentIds, ZonedDateTime receivedAt) {
    }

    public record ReviewView(RequestReviewResult result, String notes, ZonedDateTime reviewedAt) {
    }

    public record CommunicationView(CommunicationChannel channel, ZonedDateTime sentAt,
            DeliveryStatus deliveryStatus, String errorDetail) {
    }

    public record FindingView(
            UUID publicId,
            UUID requestPublicId,
            String factObserved,
            String evidence,
            FindingClassification classification,
            FindingSeverity severity,
            String impact,
            boolean remediable,
            String contractualBasis,
            ContractualMeasure measure,
            String measureScope,
            AccountStatus accountStatusApplied,
            ZonedDateTime decidedAt) {
    }

    public record IncidentView(Long id, String context, String description, UUID requestPublicId,
            Long manifestationId, boolean reportedBySystem, ZonedDateTime occurredAt) {
    }

    public record AlertRuleView(Long id, String code, int version, AlertRuleType ruleType, String description,
            String legalBasis, String paramsJson, boolean active, ZonedDateTime createdAt) {
    }

    public record AlertView(Long id, String ruleCode, Integer ruleVersion, InformationRequestTrigger trigger,
            UUID commercialPublicId, String companyName, ZonedDateTime detectedAt, String dataUsedJson,
            String message, ZonedDateTime reviewedAt, String reviewNotes, UUID requestPublicId) {
    }

    // ==================== PETICIONES ====================

    @Data
    public static class CreateRequest {
        @NotNull
        private UUID commercialPublicId;

        @NotNull
        private InformationRequestTrigger trigger;

        @NotBlank
        @Size(max = 1000)
        private String reason;

        /** Qué requisito de la vinculación o del funcionamiento se evalúa. */
        @NotBlank
        @Size(max = 1000)
        private String relationToOperation;

        /** Solo si una regla aplicable fija plazo. */
        private LocalDate dueDate;

        @NotEmpty(message = "Indique qué se pide: un requerimiento no puede ser 'actualice toda su información'")
        @Valid
        private List<ItemRequest> items = new ArrayList<>();

        /** Alerta que originó el requerimiento, si aplica: queda revisada y vinculada. */
        private Long alertId;
    }

    @Data
    public static class ItemRequest {
        @NotNull
        private RequestItemType itemType;

        private CommercialDocumentType documentType;

        @Size(max = 100)
        private String fieldPath;

        private Long establishmentId;

        @NotBlank
        @Size(max = 500)
        private String description;

        @Size(max = 500)
        private String acceptableResponse;
    }

    @Data
    public static class RespondRequest {
        @Size(max = 2000)
        private String message;

        /** Documentos del expediente (subidos por /commercials/documents) que soportan la respuesta. */
        private List<Long> documentIds = new ArrayList<>();
    }

    @Data
    public static class ReviewRequest {
        @NotNull
        private RequestReviewResult result;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    public static class CancelRequest {
        @NotBlank
        @Size(max = 1000)
        private String reason;
    }

    @Data
    public static class FindingRequest {
        @NotNull
        private UUID commercialPublicId;

        /** Requerimiento del que sale el hallazgo, si aplica: queda cerrado con el hallazgo. */
        private UUID requestPublicId;

        @NotBlank
        @Size(max = 2000)
        private String factObserved;

        @Size(max = 2000)
        private String evidence;

        @NotNull
        private FindingClassification classification;

        @NotNull
        private FindingSeverity severity;

        @NotBlank
        @Size(max = 1000)
        private String impact;

        @NotNull
        private Boolean remediable;

        @NotBlank(message = "Toda decisión necesita fundamento contractual")
        @Size(max = 1000)
        private String contractualBasis;

        @NotNull
        private ContractualMeasure measure;

        @Size(max = 500)
        private String measureScope;
    }

    @Data
    public static class IncidentRequest {
        @NotNull
        private UUID commercialPublicId;

        @NotBlank
        @Size(max = 100)
        private String context;

        @NotBlank
        @Size(max = 1000)
        private String description;

        private UUID requestPublicId;
    }

    /** Regla de crecimiento de ventas en la plataforma. Los valores los fija compliance, con fundamento. */
    @Data
    public static class AlertRuleRequest {
        @NotBlank
        @Pattern(regexp = "[A-Z0-9_]{3,50}")
        private String code;

        @NotNull
        private AlertRuleType ruleType;

        @NotBlank
        @Size(max = 500)
        private String description;

        @NotBlank
        @Size(max = 500)
        private String legalBasis;

        /** Días de cada ventana comparada (actual frente a la anterior). */
        @NotNull
        @Min(7)
        private Integer windowDays;

        /** Crecimiento mínimo, en %, para generar la alerta. */
        @NotNull
        @DecimalMin(value = "0.01")
        private BigDecimal minGrowthPct;

        /** Ventas mínimas de la ventana actual, en centavos, para que la comparación sea pertinente. */
        @NotNull
        @PositiveOrZero
        private Long minCurrentSalesCents;
    }

    @Data
    public static class AlertReviewRequest {
        @NotBlank
        @Size(max = 1000)
        private String notes;
    }
}
