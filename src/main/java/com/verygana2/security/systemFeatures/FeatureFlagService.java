package com.verygana2.security.systemFeatures;

import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.verygana2.exceptions.FeatureDisabledException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final SystemFeatureRepository repository;

    @Cacheable("featureMappings")
    public List<SystemFeature> getFeatures() {
        try {
            return repository.findAll().stream()
                    .sorted(Comparator.comparingInt((SystemFeature f) 
                        -> f.getEndpointPrefix().length()).reversed())
                    .toList();

        } catch (Exception ex) {
            log.error("Error cargando feature flags", ex);
            return List.of();
        }
    }

    public SystemFeature getFeatureForPath(String path) {
        if (path == null) return null;

        return getFeatures().stream()
                .filter(f -> path.startsWith(f.getEndpointPrefix()))
                .findFirst()
                .orElse(null);
    }

    public void validateFeature(String path, String httpMethod) {
        SystemFeature feature = getFeatureForPath(path);
        if (feature == null) return;

        switch (feature.getStatus()) {
            case ENABLED -> { return; }
            case READ_ONLY -> {
                if (!"GET".equalsIgnoreCase(httpMethod) && !"HEAD".equalsIgnoreCase(httpMethod))
                    throw new FeatureDisabledException("Módulo en modo solo lectura");
            }
            case MAINTENANCE -> throw new FeatureDisabledException("Módulo en mantenimiento");
            case DISABLED -> throw new FeatureDisabledException("Módulo deshabilitado");
        }
    }

    @CacheEvict(value = "featureMappings", allEntries = true)
    public void refreshCache() {}
}