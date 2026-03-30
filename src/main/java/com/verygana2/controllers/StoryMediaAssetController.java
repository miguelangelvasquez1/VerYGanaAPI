package com.verygana2.controllers;

import com.verygana2.dtos.impactStory.PrepareMediaUploadRequestDTO;
import com.verygana2.dtos.impactStory.PrepareMediaUploadResponseDTO;
import com.verygana2.services.interfaces.StoryMediaAssetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/impact-stories/media")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StoryMediaAssetController {

    private final StoryMediaAssetService storyMediaAssetService;

    // ── POST /api/v1/impact-stories/media/prepare-upload ─────────────────────
    // PASO 1 del flujo de subida:
    //   1. El admin llama este endpoint con los metadatos del archivo.
    //   2. El backend crea un StoryMediaAsset en PENDING y devuelve:
    //        - mediaAssetId  → se usará en el body del POST /impact-stories
    //        - permission.uploadUrl  → el frontend hace PUT directo a R2
    //        - permission.publicUrl  → URL pública definitiva del archivo

    @PostMapping("/prepare-upload")
    public ResponseEntity<PrepareMediaUploadResponseDTO> prepareUpload(
            @Valid @RequestBody PrepareMediaUploadRequestDTO request) {

        PrepareMediaUploadResponseDTO response = storyMediaAssetService.prepareUpload(request);
        return ResponseEntity.ok(response);
    }
}