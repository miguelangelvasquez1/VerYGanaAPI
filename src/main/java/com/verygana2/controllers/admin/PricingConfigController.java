package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.PricingConfig;
import com.verygana2.services.PricingConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/pricing-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PricingConfigController {

    private final PricingConfigService pricingConfigService;

    @GetMapping
    public ResponseEntity<List<PricingConfig>> getPricingConfigs() {
        return ResponseEntity.ok(pricingConfigService.getPricingConfigs());
    }

    @PatchMapping("/{type}")
    public ResponseEntity<PricingConfig> updatePricingConfig(
            @PathVariable PricingConfig.PricingType type,
            @RequestParam Long value) {

        return ResponseEntity.ok(pricingConfigService.updatePricingConfig(type, value));
    }
}
