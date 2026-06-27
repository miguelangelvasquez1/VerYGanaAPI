package com.verygana2.controllers.compliance;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.compliance.ScreeningResult;
import com.verygana2.models.enums.ScreeningStatus;
import com.verygana2.repositories.compliance.ScreeningResultRepository;
import com.verygana2.services.interfaces.compliance.ScreeningService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/compliance/screenings")
@PreAuthorize("hasRole('ROLE_COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceScreeningController {

    private final ScreeningService screeningService;
    private final ScreeningResultRepository screeningResultRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ScreeningResult>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(screeningService.getResultsByUserId(userId));
    }

    @GetMapping("/hits")
    public ResponseEntity<Page<ScreeningResult>> getUnresolvedHits(
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        Page<ScreeningResult> hits = screeningResultRepository.findUnresolvedHits(
                List.of(ScreeningStatus.HIT, ScreeningStatus.FUZZY_HIT), pageable);
        return ResponseEntity.ok(hits);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Void> reviewResult(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal Jwt jwt) {
        Long officerId = jwt.getClaim("userId");
        screeningService.reviewResult(id, officerId, notes);
        return ResponseEntity.ok().build();
    }
}