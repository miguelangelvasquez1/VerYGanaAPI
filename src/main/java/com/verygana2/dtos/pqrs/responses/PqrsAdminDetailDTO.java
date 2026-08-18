package com.verygana2.dtos.pqrs.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.enums.pqrs.PqrsResolutionAction;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;

import lombok.Data;

@Data
public class PqrsAdminDetailDTO {

    private Long id;
    private String based;
    private PqrsType type;
    private PqrsStatus status;
    private String subject;
    private String description;
    private String response;
    private ZonedDateTime dueDate;
    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;

    private Long requesterId;
    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;

    /** Nullable — solo presente si el PQRS se radicó desde /purchaseItems/{id}/report.
     *  El frontend debe mostrar el selector DISMISS/REFUND solo cuando esto no sea null. */
    private Long purchaseItemId;
    private MarketplaceIssueReason reasonCode;
    private PqrsResolutionAction action;
}
