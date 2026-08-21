package com.verygana2.controllers.pqrs;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.PreparePqrsAssetRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsController} (endpoints del usuario final: radicar y
 * consultar sus propios PQRS, más el ciclo de subida de evidencia). Es un
 * test de unidad puro: se instancia el controller a mano con los services
 * mockeados y se invoca cada método directamente, sin levantar el contexto
 * de Spring ni MockMvc — solo se verifica que el controller extrae el userId
 * del JWT correctamente, delega en el service y traduce la respuesta al
 * status HTTP esperado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsController")
class PqrsControllerTest {

    @Mock private PqrsService pqrsService;
    @Mock private PqrsAssetService pqrsAssetService;

    private PqrsController controller;

    @BeforeEach
    void setUp() {
        controller = new PqrsController(pqrsService, pqrsAssetService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("createPqrs: extrae el userId del JWT, delega en el service y responde 201 CREATED")
    void createPqrs_returns201WithBody() {
        CreatePqrsRequestDTO request = new CreatePqrsRequestDTO();
        PqrsResponseDTO expected = new PqrsResponseDTO();
        when(pqrsService.createPqrs(request, 1L)).thenReturn(expected);

        ResponseEntity<PqrsResponseDTO> response = controller.createPqrs(request, jwtWithUserId(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getMyPqrs: delega en el service con el userId del JWT y el pageable recibido, responde 200")
    void getMyPqrs_returns200WithPagedBody() {
        Pageable pageable = PageRequest.of(0, 20);
        PagedResponse<PqrsResponseDTO> expected = PagedResponse.<PqrsResponseDTO>builder().build();
        when(pqrsService.getMyPqrs(2L, pageable)).thenReturn(expected);

        ResponseEntity<PagedResponse<PqrsResponseDTO>> response = controller.getMyPqrs(jwtWithUserId(2L), pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(pqrsService).getMyPqrs(2L, pageable);
    }

    @Test
    @DisplayName("getMyPqrsDetail: delega en el service con el id del path y el userId del JWT, responde 200")
    void getMyPqrsDetail_returns200WithBody() {
        PqrsResponseDTO expected = new PqrsResponseDTO();
        when(pqrsService.getMyPqrsDetail(5L, 3L)).thenReturn(expected);

        ResponseEntity<PqrsResponseDTO> response = controller.getMyPqrsDetail(5L, jwtWithUserId(3L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("prepareAssetUpload: delega en PqrsAssetService con el userId del JWT, responde 200")
    void prepareAssetUpload_returns200WithBody() {
        PreparePqrsAssetRequestDTO request = new PreparePqrsAssetRequestDTO();
        PqrsAssetUploadPermissionDTO expected = new PqrsAssetUploadPermissionDTO(
                1L, new FileUploadPermissionDTO("https://upload-url", 900L));
        when(pqrsAssetService.prepareUpload(4L, request)).thenReturn(expected);

        ResponseEntity<PqrsAssetUploadPermissionDTO> response = controller.prepareAssetUpload(request, jwtWithUserId(4L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("confirmAssetUpload: delega en PqrsAssetService con el id del path y el userId del JWT, responde 200")
    void confirmAssetUpload_returns200WithBody() {
        PqrsAssetResponseDTO expected = new PqrsAssetResponseDTO();
        when(pqrsAssetService.confirmUpload(4L, 10L)).thenReturn(expected);

        ResponseEntity<PqrsAssetResponseDTO> response = controller.confirmAssetUpload(10L, jwtWithUserId(4L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Nested
    @DisplayName("streamAsset (proxy de evidencia privada)")
    class StreamAsset {

        private void mockAuthorities(String... authorities) {
            SecurityContext context = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            List<GrantedAuthority> granted = List.of(authorities).stream()
                    .map(a -> {
                        GrantedAuthority ga = mock(GrantedAuthority.class);
                        when(ga.getAuthority()).thenReturn(a);
                        return ga;
                    })
                    .toList();
            doReturn(granted).when(authentication).getAuthorities();
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);
        }

        @Test
        @DisplayName("consumer dueño del archivo: delega con isAdmin=false")
        void owner_delegatesWithIsAdminFalse() throws Exception {
            mockAuthorities("ROLE_CONSUMER");
            try {
                HttpServletResponse response = mock(HttpServletResponse.class);

                controller.streamAsset(1L, jwtWithUserId(9L), response);

                verify(pqrsAssetService).streamAsset(eq(1L), eq(9L), eq(false), eq(response));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("admin: delega con isAdmin=true")
        void admin_delegatesWithIsAdminTrue() throws Exception {
            mockAuthorities("ROLE_ADMIN");
            try {
                HttpServletResponse response = mock(HttpServletResponse.class);

                controller.streamAsset(1L, jwtWithUserId(50L), response);

                verify(pqrsAssetService).streamAsset(eq(1L), eq(50L), eq(true), eq(response));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
