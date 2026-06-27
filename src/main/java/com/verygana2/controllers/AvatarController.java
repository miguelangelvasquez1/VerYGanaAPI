package com.verygana2.controllers;

import com.verygana2.dtos.avatar.AvatarResponseDTO;
import com.verygana2.dtos.avatar.UpdateAvatarRequestDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.services.interfaces.AvatarService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/avatars")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping
    public ResponseEntity<List<AvatarResponseDTO>> listActiveAvatars() {
        List<AvatarResponseDTO> avatars = avatarService.listActiveAvatars()
                .stream()
                .map(avatar -> new AvatarResponseDTO(
                        avatar.getId(),
                        avatar.getName(),
                        avatar.getImageUrl()
                ))
                .toList();

        return ResponseEntity.ok(avatars);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<EntityUpdatedResponseDTO> updateMyAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAvatarRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(avatarService.updateConsumerAvatar(consumerId, request.getAvatarId()));
    }
}