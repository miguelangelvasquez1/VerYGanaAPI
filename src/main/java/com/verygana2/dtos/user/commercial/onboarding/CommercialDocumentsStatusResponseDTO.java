package com.verygana2.dtos.user.commercial.onboarding;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommercialDocumentsStatusResponseDTO {
    private List<CommercialDocumentResponseDTO> documents;
    private List<DocumentChecklistItemDTO> checklist;
    private boolean allRequiredUploaded;
}
