package com.verygana2.controllers;

import com.verygana2.dtos.avatar.AvatarResponseDTO;
import com.verygana2.services.interfaces.AvatarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}