package com.verygana2.controllers.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.ConfirmRaffleCreationRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.PrepareRaffleCreationRequestBodyDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleAssetsUploadPermissionDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.dtos.raffle.responses.SuspiciousIpActivityResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketAuditLogResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.services.interfaces.raffles.DrawingService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleAdminController}: delegación de cada endpoint al
 * service correspondiente (DrawingService/RaffleService/RaffleTicketService),
 * siguiendo el mismo patrón que {@code RaffleControllerTest}. No cubre
 * {@code @PreAuthorize}/{@code @Valid} (requeriría un contexto Spring/MockMvc).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleAdminController")
class RaffleAdminControllerTest {

    @Mock private DrawingService drawingService;
    @Mock private RaffleService raffleService;
    @Mock private RaffleTicketService raffleTicketService;

    private RaffleAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new RaffleAdminController(drawingService, raffleService, raffleTicketService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    private CreateRaffleRequestDTO sampleRaffleData() {
        return new CreateRaffleRequestDTO("Rifa", "Descripcion", RaffleType.STANDARD,
                ZonedDateTime.now(), ZonedDateTime.now().plusDays(5), ZonedDateTime.now().plusDays(6),
                100L, 5L, false, null, List.of(), List.of(), "Terminos", null);
    }

    @Test
    @DisplayName("prepareRaffleCreation: extrae el adminId del JWT y delega en RaffleService")
    void prepareRaffleCreation_delegates() {
        var request = new PrepareRaffleCreationRequestBodyDTO(
                sampleRaffleData(),
                new FileUploadRequestDTO("raffle.png", "image/png", 1024L, 10, null),
                List.of(new FileUploadRequestDTO("prize.png", "image/png", 1024L, 10, null)));
        var expected = RaffleAssetsUploadPermissionDTO.builder().raffleAssetId(1L).build();
        when(raffleService.prepareRaffleCreation(9L, request.getRaffleData(), request.getRaffleImageMetadata(),
                request.getPrizeImageMetadataList())).thenReturn(expected);

        var response = controller.prepareRaffleCreation(jwtWithUserId(9L), request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("confirmRaffleCreation: extrae el adminId del JWT, delega en RaffleService y responde 201")
    void confirmRaffleCreation_delegates() {
        var request = new ConfirmRaffleCreationRequestDTO(1L, List.of(2L), sampleRaffleData());
        var expected = new EntityCreatedResponseDTO(5L, "Raffle created", Instant.now());
        when(raffleService.confirmRaffleCreation(9L, request)).thenReturn(expected);

        var response = controller.confirmRaffleCreation(jwtWithUserId(9L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("updateRaffle: extrae el adminId del JWT y delega con el raffleId del path")
    void updateRaffle_delegates() {
        var request = new UpdateRaffleRequestDTO("Titulo", "Descripcion", RaffleType.STANDARD, false,
                ZonedDateTime.now(), ZonedDateTime.now().plusDays(5), ZonedDateTime.now().plusDays(6), null);
        var expected = EntityUpdatedResponseDTO.builder().id(1L).message("Raffle updated successfully").build();
        when(raffleService.updateRaffle(9L, 1L, request)).thenReturn(expected);

        var response = controller.updateRaffle(jwtWithUserId(9L), 1L, request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("activateRaffle: delega en RaffleService y responde 204")
    void activateRaffle_delegates() {
        var response = controller.activateRaffle(1L);

        verify(raffleService).activateRaffle(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("closeRaffle: delega en RaffleService y responde 204")
    void closeRaffle_delegates() {
        var response = controller.closeRaffle(1L);

        verify(raffleService).closeRaffle(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("cancelRaffle: delega en RaffleService y responde 204")
    void cancelRaffle_delegates() {
        var response = controller.cancelRaffle(1L);

        verify(raffleService).cancelRaffle(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("deleteRaffle: delega en RaffleService y responde 204")
    void deleteRaffle_delegates() {
        var response = controller.deleteRaffle(1L);

        verify(raffleService).deleteRaffle(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("conductDraw: delega en DrawingService con el raffleId del path")
    void conductDraw_delegates() {
        var expected = DrawResultResponseDTO.builder().raffleId(1L).numberOfWinners(2).build();
        when(drawingService.conductDraw(1L)).thenReturn(expected);

        var response = controller.conductDraw(1L);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("verifyDrawIntegrity: delega en DrawingService con el raffleId del path")
    void verifyDrawIntegrity_delegates() {
        when(drawingService.verifyDrawIntegrity(1L)).thenReturn(true);

        var response = controller.verifyDrawIntegrity(1L);

        assertThat(response.getBody()).isTrue();
    }

    @Test
    @DisplayName("countRafflesByStatus: delega en RaffleService con el status recibido")
    void countRafflesByStatus_delegates() {
        when(raffleService.countRafflesByStatus(RaffleStatus.ACTIVE)).thenReturn(7L);

        var response = controller.countRafflesByStatus(RaffleStatus.ACTIVE);

        assertThat(response.getBody()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getTicketAuditLogs: delega en RaffleTicketService con el ticketId del path")
    void getTicketAuditLogs_delegates() {
        var expected = List.of(new TicketAuditLogResponseDTO());
        when(raffleTicketService.getAuditLogsByTicketId(3L)).thenReturn(expected);

        var response = controller.getTicketAuditLogs(3L);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getAuditLogsBetweenDates: delega en RaffleTicketService con el rango de fechas y el pageable")
    void getAuditLogsBetweenDates_delegates() {
        var from = LocalDate.of(2026, 1, 1);
        var to = LocalDate.of(2026, 1, 31);
        var pageable = PageRequest.of(0, 20);
        var expected = PagedResponse.<TicketAuditLogResponseDTO>builder().build();
        when(raffleTicketService.getAuditLogsBetweenDates(from, to, pageable)).thenReturn(expected);

        var response = controller.getAuditLogsBetweenDates(from, to, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getSuspiciousActivity: delega en RaffleTicketService con since/threshold")
    void getSuspiciousActivity_delegates() {
        var since = LocalDate.of(2026, 1, 1);
        var expected = List.of(new SuspiciousIpActivityResponseDTO("1.2.3.4", 10L));
        when(raffleTicketService.getSuspiciousActivity(since, 5L)).thenReturn(expected);

        var response = controller.getSuspiciousActivity(since, 5L);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getRaffleStats: delega en RaffleService con el raffleId del path")
    void getRaffleStats_delegates() {
        var expected = new RaffleStatsResponseDTO(1L, 10L, 5L, 2L, null, null);
        when(raffleService.getRaffleStats(1L)).thenReturn(expected);

        var response = controller.getRaffleStats(1L);

        assertThat(response.getBody()).isSameAs(expected);
    }
}
