package com.verygana2.controllers.pqrs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsController} (endpoints del usuario final: radicar y
 * consultar sus propios PQRS). Es un test de unidad puro: se instancia el
 * controller a mano con el service mockeado y se invoca cada método
 * directamente, sin levantar el contexto de Spring ni MockMvc — solo se
 * verifica que el controller extrae el userId del JWT correctamente,
 * delega en el service y traduce la respuesta al status HTTP esperado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsController")
class PqrsControllerTest {

    @Mock private PqrsService pqrsService;

    private PqrsController controller;

    @BeforeEach
    void setUp() {
        controller = new PqrsController(pqrsService);
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
}
