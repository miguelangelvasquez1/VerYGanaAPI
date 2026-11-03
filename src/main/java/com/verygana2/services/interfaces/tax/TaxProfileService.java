package com.verygana2.services.interfaces.tax;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.ComplianceTaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileSummary;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileVersionDetail;
import com.verygana2.dtos.tax.TaxProfileDTOs.VersionSummary;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO;
import com.verygana2.dtos.tax.TaxRequestDTOs.SectionReviewRequest;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;

/**
 * Ficha tributaria del empresario (4.7 a–h). Información declarada por el empresario y
 * revisada por compliance por secciones. Nunca modifica la vocación, la comisión, el plan
 * ni la condición tributaria por su cuenta.
 */
public interface TaxProfileService {

    /** Ficha vigente, o null si el empresario todavía no la envió. */
    TaxProfileResponse getOwn(Long commercialId);

    TaxProfileResponse submit(Long commercialId, TaxProfileSubmitRequestDTO request, String ipAddress, String userAgent);

    List<VersionSummary> listVersions(Long commercialId);

    TaxProfileVersionDetail getVersion(Long commercialId, int versionNumber);

    boolean hasProfile(Long commercialId);

    // ---- Compliance ----

    PagedResponse<TaxProfileSummary> list(TaxProfileStatus status, Pageable pageable);

    ComplianceTaxProfileResponse getForCompliance(UUID commercialPublicId);

    TaxProfileVersionDetail getVersionForCompliance(UUID commercialPublicId, int versionNumber);

    ComplianceTaxProfileResponse reviewSection(UUID commercialPublicId, TaxProfileSection section,
            Long reviewerUserId, SectionReviewRequest request);
}
