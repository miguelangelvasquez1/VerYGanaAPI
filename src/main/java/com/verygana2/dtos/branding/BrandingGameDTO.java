package com.verygana2.dtos.branding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandingGameDTO {

    private Long id;
    private String title;
    private String description;
    private String frontPageUrl;
    private String url;
    private Long averageRewardPerSessionCents;
    private Integer averageDurationSeconds;
}