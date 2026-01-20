package com.verygana2.dtos.ad.requests;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdLikeRequest {
    
    private UUID sessionUUID;
    private Long adId;
}