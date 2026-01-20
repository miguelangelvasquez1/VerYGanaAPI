package com.verygana2.dtos.ad.responses;

import java.util.UUID;

import com.verygana2.models.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdForConsumerDTO {
    
    private Long id;
    private String title;
    private String description;
    private Integer currentLikes;
    private String contentUrl;
    private String targetUrl;
    private MediaType mediaType;

    private UUID sessionUUID;

    private Long advertiserId;
    private String advertiserName; 
}
