package com.verygana2.dtos.finance.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ConfirmPayoutMethodCertificateUploadRequestDTO {
    @NotNull
    private Long certificateAssetId;
}
