package com.verygana2.dtos.legal;

import java.time.LocalDate;

import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocumentResponseDTO {
    private Long id;
    private LegalDocumentType type;
    private String version;
    private String documentUrl;
    private LegalDocumentStatus status;
    private LocalDate publishedDate;
    private boolean active;
}
