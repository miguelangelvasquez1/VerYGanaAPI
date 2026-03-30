package com.verygana2.controllers;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.models.ImpactStory.StoryStatus;
import com.verygana2.services.interfaces.ImpactStoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/impact-stories")
@RequiredArgsConstructor
public class ImpactStoryController {

    private final ImpactStoryService impactStoryService;

    // Público: lista las historias publicadas (paginada)
    // Admin:   puede filtrar por cualquier status

    @GetMapping("/consumer")
    public ResponseEntity<Page<ImpactStoryResponseDTO>> findAllForConsumer(
            @PageableDefault(size = 10, sort = "storyDate", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ImpactStoryResponseDTO> page = impactStoryService.findAllForConsumer(pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    
    public ResponseEntity<Page<ImpactStoryResponseDTO>> findAll(
            @RequestParam(required = false) StoryStatus status,
            @PageableDefault(size = 10, sort = "storyDate", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ImpactStoryResponseDTO> page = status != null
            ? impactStoryService.findByStatus(status, pageable)
            : impactStoryService.findAll(pageable);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImpactStoryResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(impactStoryService.findById(id));
    }

    // Solo administradores. PASO 3 del flujo: los assets ya deben estar en R2.

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImpactStoryResponseDTO> create(
            @Valid @RequestBody CreateImpactStoryRequestDTO request) {

        ImpactStoryResponseDTO response = impactStoryService.create(request);

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.getId())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    // ── PUT /api/v1/impact-stories/{id} ───────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImpactStoryResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateImpactStoryRequestDTO request) {

        return ResponseEntity.ok(impactStoryService.update(id, request));
    }

    // ── DELETE /api/v1/impact-stories/{id} ────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        impactStoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}