package com.verygana2.config.pqrs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "pqrs.assets")
@Data
public class PqrsAssetProperties {

    private long maxSizeBytes = 26_214_400; // 25 MB — mismo orden que pets.designer-asset (video-capable)
    private int maxCount = 5;
}
