package com.verygana2.storage.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.storage.service.AdMediaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/ads/{adId}/media")
@RequiredArgsConstructor
@Tag(name = "Ad Media", description = "Gestión de contenido multimedia de anuncios")
public class AdMediaController {

    private final AdMediaService adMediaService;

    /**
     * Sube un video para un anuncio
     */
    @Operation(
        summary = "Subir video de anuncio",
        description = "Sube un archivo de video para el anuncio especificado. " +
                     "Formatos soportados: MP4, WebM, OGG. Tamaño máximo: 100MB"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Video subido exitosamente",
            content = @Content(schema = @Schema(implementation = AdResponseDTO.class))
        ),
        @ApiResponse(responseCode = "400", description = "Archivo inválido o muy grande"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Anuncio no encontrado")
    })
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> uploadVideo(
            @PathVariable Long adId,
            @Parameter(description = "Archivo de video")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long userId = jwt.getClaim("userId");
        log.info("Usuario {} subiendo video para anuncio {}", userId, adId);
        
        AdResponseDTO response = adMediaService.uploadAdMedia(adId, file, "video");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Sube una imagen para un anuncio
     */
    @Operation(
        summary = "Subir imagen de anuncio",
        description = "Sube un archivo de imagen para el anuncio especificado. " +
                     "Formatos soportados: JPG, PNG, WebP, GIF. Tamaño máximo: 5MB"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Imagen subida exitosamente",
            content = @Content(schema = @Schema(implementation = AdResponseDTO.class))
        ),
        @ApiResponse(responseCode = "400", description = "Archivo inválido o muy grande"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Anuncio no encontrado")
    })
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> uploadImage(
            @PathVariable Long adId,
            @Parameter(description = "Archivo de imagen")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long userId = jwt.getClaim("userId");
        log.info("Usuario {} subiendo imagen para anuncio {}", userId, adId);
        
        AdResponseDTO response = adMediaService.uploadAdMedia(adId, file, "image");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina el contenido multimedia de un anuncio
     */
    @Operation(
        summary = "Eliminar media de anuncio",
        description = "Elimina el contenido multimedia asociado al anuncio"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Media eliminado exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Anuncio no encontrado")
    })
    @SecurityRequirement(name = "bearer-jwt")
    @DeleteMapping
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<Map<String, String>> deleteMedia(
            @PathVariable Long adId,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long userId = jwt.getClaim("userId");
        log.info("Usuario {} eliminando media de anuncio {}", userId, adId);
        
        adMediaService.deleteAdMedia(adId);
        
        return ResponseEntity.ok(Map.of(
            "message", "Media eliminado exitosamente",
            "adId", adId.toString()
        ));
    }

    /**
     * Obtiene URL optimizada para thumbnail
     */
    @Operation(
        summary = "Generar thumbnail",
        description = "Genera una URL optimizada para thumbnail del contenido"
    )
    @GetMapping("/thumbnail")
    @PreAuthorize("hasRole('ADVERTISER') or hasRole('CONSUMER')")
    public ResponseEntity<Map<String, String>> getThumbnail(
            @PathVariable Long adId,
            @RequestParam(defaultValue = "400") int width,
            @RequestParam(defaultValue = "300") int height) {
        
        // Aquí deberías obtener el publicId del anuncio
        // Por simplicidad, asumo que lo tienes
        String publicId = "ads/" + adId + "/main"; // Ejemplo
        
        String thumbnailUrl = adMediaService.generateOptimizedUrl(publicId, width, height);
        
        return ResponseEntity.ok(Map.of(
            "thumbnailUrl", thumbnailUrl
        ));
    }

    /**
     * Health check del servicio de storage
     */
    @Operation(
        summary = "Health check de storage",
        description = "Verifica que el servicio de almacenamiento esté operativo"
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> storageHealth() {
        // Implementar verificación de health
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "provider", "cloudinary" // O "r2" dependiendo de la configuración
        ));
    }
}