package com.verygana2.services.pqrs;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.User;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsAssignmentService}: la rotación round-robin que decide
 * a qué admin activo se le asigna el siguiente PQRS.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsAssignmentService")
class PqrsAssignmentServiceTest {

    @Mock private AdminDetailsRepository adminDetailsRepository;

    private PqrsAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new PqrsAssignmentService(adminDetailsRepository);
    }

    private AdminDetails admin(Long userId) {
        User user = new User();
        user.setId(userId);
        AdminDetails details = new AdminDetails();
        details.setUser(user);
        return details;
    }

    @Test
    @DisplayName("sin admins activos disponibles: retorna Optional.empty() y no guarda nada")
    void noActiveAdmins_returnsEmpty() {
        when(adminDetailsRepository.findActiveAdminsForPqrsAssignmentForUpdate(any(PageRequest.class)))
                .thenReturn(List.of());

        assertThat(service.pickNextAdmin()).isEmpty();
        verify(adminDetailsRepository, never()).save(any());
    }

    @Test
    @DisplayName("con admin candidato: actualiza lastPqrsAssignedAt y lo devuelve")
    void withCandidate_updatesLastAssignedAtAndReturnsIt() {
        AdminDetails candidate = admin(50L);
        when(adminDetailsRepository.findActiveAdminsForPqrsAssignmentForUpdate(any(PageRequest.class)))
                .thenReturn(List.of(candidate));
        when(adminDetailsRepository.save(candidate)).thenReturn(candidate);

        var result = service.pickNextAdmin();

        assertThat(result).contains(candidate);
        ArgumentCaptor<AdminDetails> captor = ArgumentCaptor.forClass(AdminDetails.class);
        verify(adminDetailsRepository).save(captor.capture());
        // El sello de "última vez asignado" debe quedar seteado para que este admin
        // pase al final de la fila la próxima vez que se pida un candidato.
        assertThat(captor.getValue().getLastPqrsAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("pide solo 1 candidato a la vez (PageRequest.of(0, 1)), no una lista completa")
    void requestsOnlyOneCandidateAtATime() {
        AdminDetails candidate = admin(1L);
        when(adminDetailsRepository.findActiveAdminsForPqrsAssignmentForUpdate(PageRequest.of(0, 1)))
                .thenReturn(List.of(candidate));
        when(adminDetailsRepository.save(candidate)).thenReturn(candidate);

        service.pickNextAdmin();

        verify(adminDetailsRepository).findActiveAdminsForPqrsAssignmentForUpdate(PageRequest.of(0, 1));
    }
}
