package com.verygana2.utils.validators.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.games.Asset;
import com.verygana2.models.games.Campaign;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.utils.validators.games.ValidationPipeline.ErrorType;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asset validator
 * Validates that all asset URLs in config exist and have correct ownership/type
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetValidator {
    
    private final AssetRepository assetRepository;
    
    public List<ValidationError> validate(Map<String, Object> configData, Campaign campaign) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Extract all URLs from config
        Set<String> assetUrls = extractAssetUrls(configData);
        
        if (assetUrls.isEmpty()) {
            log.debug("No assets found in config");
            return errors;
        }
        
        log.debug("Validating {} asset URLs", assetUrls.size());
        
        // Fetch all assets by URLs
        List<Asset> assets = assetRepository.findByObjectKeyIn(assetUrls);
        Map<String, Asset> assetMap = new HashMap<>();
        for (Asset asset : assets) {
            assetMap.put(asset.getObjectKey(), asset);
        }
        
        // Validate each URL
        for (String url : assetUrls) {
            Asset asset = assetMap.get(url);
            
            if (asset == null) {
                errors.add(new ValidationError(
                    "asset_url",
                    "Asset not found: " + url,
                    ErrorType.ASSET
                ));
                continue;
            }
            
            // Check asset status
            if (asset.getStatus() == AssetStatus.ORPHANED) {
                errors.add(new ValidationError(
                    "asset_url",
                    "Asset is orphaned and cannot be used: " + url,
                    ErrorType.ASSET
                ));
            }
            
            // Check ownership (if campaign already exists)
            if (campaign != null && campaign.getId() != null) {
                if (asset.getCampaign() != null && 
                    !asset.getCampaign().getId().equals(campaign.getId())) {
                    errors.add(new ValidationError(
                        "asset_url",
                        "Asset belongs to different campaign: " + url,
                        ErrorType.ASSET
                    ));
                }
            }
        }
        
        // Validate asset types based on field names (heuristic)
        validateAssetTypes(configData, assetMap, errors);
        
        return errors;
    }
    
    /**
     * Extract all asset URLs from config recursively
     */
    private Set<String> extractAssetUrls(Object obj) {
        Set<String> urls = new HashSet<>();
        
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                
                // Check if this is likely an asset URL field
                if (isAssetField(key) && value instanceof String) {
                    String url = (String) value;
                    if (isValidAssetUrl(url)) {
                        urls.add(url);
                    }
                } else {
                    urls.addAll(extractAssetUrls(value));
                }
            }
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (Object item : list) {
                urls.addAll(extractAssetUrls(item));
            }
        }
        
        return urls;
    }
    
    private boolean isAssetField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("url") || 
               lower.contains("image") || 
               lower.contains("icon") || 
               lower.contains("sprite") || 
               lower.contains("texture") || 
               lower.contains("audio") || 
               lower.contains("sound") || 
               lower.contains("music") ||
               lower.contains("model") ||
               lower.contains("background");
    }
    
    private boolean isValidAssetUrl(String url) {
        // Basic validation - should point to R2 storage
        return url != null && 
               !url.isBlank() && 
               (url.startsWith("https://") || url.startsWith("http://"));
    }
    
    private void validateAssetTypes(
        Map<String, Object> configData,
        Map<String, Asset> assetMap,
        List<ValidationError> errors
    ) {
        // Validate specific sections
        validateAssetTypesInSection(configData, "branding", MediaType.IMAGE, assetMap, errors);
        validateAssetTypesInSection(configData, "audio", MediaType.AUDIO, assetMap, errors);
        validateAssetTypesInSection(configData, "game_config", null, assetMap, errors);
    }
    
    private void validateAssetTypesInSection(
        Map<String, Object> configData,
        String section,
        MediaType expectedType,
        Map<String, Asset> assetMap,
        List<ValidationError> errors
    ) {
        Object sectionObj = configData.get(section);
        if (sectionObj instanceof Map) {
            Set<String> urls = extractAssetUrls(sectionObj);
            for (String url : urls) {
                Asset asset = assetMap.get(url);
                if (asset != null && expectedType != null && asset.getMediaType() != expectedType) {
                    errors.add(new ValidationError(
                        section + ".asset",
                        String.format("Invalid asset type in %s. Expected %s but got %s: %s",
                            section, expectedType, asset.getMediaType(), url),
                        ErrorType.ASSET
                    ));
                }
            }
        }
    }
}