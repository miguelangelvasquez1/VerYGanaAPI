package com.verygana2.security.systemFeatures;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.security.systemFeatures.SystemFeature.FeatureStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/system-features")
@RequiredArgsConstructor
public class SystemFeatureController {

    private final SystemFeatureRepository repository;
    private final FeatureFlagService featureFlagService;
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<SystemFeature> getAll() {
        return featureFlagService.getFeatures();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateStatus(@PathVariable Long id, @RequestParam FeatureStatus status) {

        SystemFeature feature = repository.findById(id).orElseThrow();
        feature.setStatus(status);
        repository.save(feature);
        featureFlagService.refreshCache();
    }
}
