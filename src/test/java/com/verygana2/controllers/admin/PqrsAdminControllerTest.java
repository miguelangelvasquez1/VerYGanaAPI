package com.verygana2.controllers.admin;

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
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsAdminController} (endpoints del admin para gestionar
 * los PQRS que le fueron asignados por rotación). Igual que en
 * {@code PqrsControllerTest}, es un test de unidad puro que mockea el
 * service y verifica delegación + status HTTP, sin levantar Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsAdminController")
class PqrsAdminControllerTest {

    @Mock private PqrsService pqrsService;

    private PqrsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PqrsAdminController(pqrsService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getAssignedPqrs: pasa el adminId del JWT y los query params (status/type/pageable) al service")
    void getAssignedPqrs_delegatesWithFiltersAndReturns200() {
        Pageable pageable = PageRequest.of(0, 20);
        PagedResponse<PqrsAdminDetailDTO> expected = PagedResponse.<PqrsAdminDetailDTO>builder().build();
        when(pqrsService.getAssignedPqrs(99L, PqrsStatus.RECIBIDA, PqrsType.QUEJA, pageable)).thenReturn(expected);

        ResponseEntity<PagedResponse<PqrsAdminDetailDTO>> response = controller.getAssignedPqrs(
                jwtWithUserId(99L), PqrsStatus.RECIBIDA, PqrsType.QUEJA, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getPqrsDetail: delega en el service con el id del path y el adminId del JWT")
    void getPqrsDetail_returns200WithBody() {
        PqrsAdminDetailDTO expected = new PqrsAdminDetailDTO();
        when(pqrsService.getPqrsDetailForAdmin(7L, 99L)).thenReturn(expected);

        ResponseEntity<PqrsAdminDetailDTO> response = controller.getPqrsDetail(7L, jwtWithUserId(99L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("markUnderReview: delega en el service y responde 200 sin body")
    void markUnderReview_delegatesAndReturns200() {
        ResponseEntity<Void> response = controller.markUnderReview(7L, jwtWithUserId(99L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pqrsService).markUnderReview(7L, 99L);
    }

    @Test
    @DisplayName("respondToPqrs: delega en el service con el body de la respuesta y responde 200 sin body")
    void respondToPqrs_delegatesAndReturns200() {
        RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
        dto.setResponse("Ya se solucionó tu caso");

        ResponseEntity<Void> response = controller.respondToPqrs(7L, dto, jwtWithUserId(99L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pqrsService).respondToPqrs(7L, dto, 99L);
    }
}
