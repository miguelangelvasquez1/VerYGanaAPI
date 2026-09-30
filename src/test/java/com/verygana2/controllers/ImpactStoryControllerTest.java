package com.verygana2.controllers;

import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.verygana2.services.interfaces.ImpactStoryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImpactStoryController.findById — el rol decide qué historias se ven")
class ImpactStoryControllerTest {

    @Mock private ImpactStoryService impactStoryService;

    @InjectMocks private ImpactStoryController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticatedAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    @DisplayName("un ROLE_ADMIN pide la historia como admin")
    void admin_isPassedAsAdmin() {
        authenticatedAs("ROLE_ADMIN");

        controller.findById(1L);

        verify(impactStoryService).findById(1L, true);
    }

    @Test
    @DisplayName("un consumidor la pide como no-admin (solo verá las PUBLISHED)")
    void consumer_isPassedAsNonAdmin() {
        authenticatedAs("ROLE_CONSUMER");

        controller.findById(1L);

        verify(impactStoryService).findById(1L, false);
    }
}
