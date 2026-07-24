package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.commercial.CommercialDocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChecklistItemDTO {
    private CommercialDocumentType documentType;
    private boolean required;
    private boolean uploaded;
}
