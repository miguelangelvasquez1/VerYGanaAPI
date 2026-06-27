package com.verygana2.controllers.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.GameDesignerRegisterDTO;
import com.verygana2.services.interfaces.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/game-designers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class GameDesignerAdminController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<String> registerGameDesigner(@Valid @RequestBody GameDesignerRegisterDTO dto) {
        userService.registerGameDesigner(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Game designer registered. A password setup link has been sent to " + dto.getEmail());
    }
}