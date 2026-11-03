package com.verygana2.services.inforequests;

import java.util.List;
import java.util.UUID;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertRuleView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.AlertView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.CommunicationView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.IncidentView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ResponseView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ReviewView;
import com.verygana2.models.inforequests.ComplianceAlert;
import com.verygana2.models.inforequests.ComplianceAlertRule;
import com.verygana2.models.inforequests.ComplianceFinding;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.models.inforequests.TechnicalIncident;

/** Mapeo de requerimientos, hallazgos, incidencias y alertas a DTOs. */
final class InformationRequestViews {

    private InformationRequestViews() {
    }

    /** {@code includeCommunications}: solo compliance ve el historial de envíos. */
    static RequestView request(InformationRequest r, UUID commercialPublicId, boolean includeCommunications) {
        List<ItemView> items = r.getItems().stream()
                .map(i -> new ItemView(i.getItemType(), i.getDocumentType(), i.getFieldPath(), i.getEstablishmentId(),
                        i.getDescription(), i.getAcceptableResponse(), i.getLinkedDocumentId()))
                .toList();
        List<ResponseView> responses = r.getResponses().stream()
                .map(x -> new ResponseView(x.getMessage(), List.copyOf(x.getDocumentIds()), x.getReceivedAt()))
                .toList();
        List<ReviewView> reviews = r.getReviews().stream()
                .map(x -> new ReviewView(x.getResult(), x.getNotes(), x.getReviewedAt()))
                .toList();
        List<CommunicationView> communications = !includeCommunications ? List.of()
                : r.getCommunications().stream()
                        .map(c -> new CommunicationView(c.getChannel(), c.getSentAt(), c.getDeliveryStatus(),
                                c.getErrorDetail()))
                        .toList();
        return new RequestView(r.getPublicId(), commercialPublicId, r.getCommercial().getCompanyName(),
                r.getTrigger(), r.getRuleCode(), r.getRuleVersion(), r.getReason(), r.getRelationToOperation(),
                r.getStatus(), r.getDueDate(), r.getIssuedByUserId() == null, r.getIssuedAt(), r.getClosedAt(),
                r.getClosingNotes(), items, responses, reviews, communications);
    }

    static FindingView finding(ComplianceFinding f) {
        return new FindingView(f.getPublicId(), f.getRequest() != null ? f.getRequest().getPublicId() : null,
                f.getFactObserved(), f.getEvidence(), f.getClassification(), f.getSeverity(), f.getImpact(),
                f.isRemediable(), f.getContractualBasis(), f.getMeasure(), f.getMeasureScope(),
                f.getAccountStatusApplied(), f.getDecidedAt());
    }

    static IncidentView incident(TechnicalIncident i) {
        return new IncidentView(i.getId(), i.getContext(), i.getDescription(),
                i.getRequest() != null ? i.getRequest().getPublicId() : null, i.getManifestationId(),
                i.getReportedByUserId() == null, i.getOccurredAt());
    }

    static AlertRuleView rule(ComplianceAlertRule r) {
        return new AlertRuleView(r.getId(), r.getCode(), r.getVersion(), r.getRuleType(), r.getDescription(),
                r.getLegalBasis(), r.getParamsJson(), r.isActive(), r.getCreatedAt());
    }

    static AlertView alert(ComplianceAlert a, UUID commercialPublicId) {
        return new AlertView(a.getId(),
                a.getRule() != null ? a.getRule().getCode() : null,
                a.getRule() != null ? a.getRule().getVersion() : null,
                a.getTrigger(), commercialPublicId, a.getCommercial().getCompanyName(), a.getDetectedAt(),
                a.getDataUsedJson(), a.getMessage(), a.getReviewedAt(), a.getReviewNotes(),
                a.getRequest() != null ? a.getRequest().getPublicId() : null);
    }
}
