package com.verygana2.services.raffles;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.PrizeMapper;
import com.verygana2.mappers.raffles.RaffleMapper;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;
import com.verygana2.security.CodeEncryptor;
import com.verygana2.storage.service.R2Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleServiceImpl}: la máquina de estados de una rifa
 * (DRAFT→ACTIVE→CLOSED / CANCELLED / COMPLETED), las validaciones de negocio
 * al crear una rifa (fechas, posiciones de premios, límites de reglas), y las
 * consultas con su validación de argumentos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleServiceImpl")
class RaffleServiceImplTest {

    @Mock private RaffleRepository raffleRepository;
    @Mock private PrizeRepository prizeRepository;
    @Mock private TicketEarningRuleRepository ticketEarningRuleRepository;
    @Mock private RaffleTicketRepository raffleTicketRepository;
    @Mock private RaffleParticipationRepository raffleParticipationRepository;
    @Mock private RaffleImageAssetRepository raffleImageAssetRepository;
    @Mock private PrizeImageAssetRepository prizeImageAssetRepository;
    @Mock private R2Service r2Service;
    @Mock private RaffleMapper raffleMapper;
    @Mock private PrizeMapper prizeMapper;
    @Mock private CodeEncryptor claimCodeEncryptor;

    private RaffleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaffleServiceImpl(raffleRepository, prizeRepository, ticketEarningRuleRepository,
                raffleTicketRepository, raffleParticipationRepository, raffleImageAssetRepository,
                prizeImageAssetRepository, r2Service, raffleMapper, prizeMapper, claimCodeEncryptor);
    }

    private Raffle raffle(Long id, RaffleStatus status) {
        Raffle raffle = new Raffle();
        raffle.setId(id);
        raffle.setRaffleStatus(status);
        raffle.setEndDate(ZonedDateTime.now().plusDays(10));
        return raffle;
    }

    // ─── Máquina de estados ─────────────────────────────────────────────────

    @Nested
    @DisplayName("activateRaffle")
    class Activate {

        @Test
        @DisplayName("DRAFT con premios y sin expirar: pasa a ACTIVE")
        void draftWithPrizesNotExpired_activatesIt() {
            Raffle raffle = raffle(1L, RaffleStatus.DRAFT);
            raffle.setPrizes(List.of(new Prize()));
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            service.activateRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.ACTIVE);
        }

        @Test
        @DisplayName("CANCELLED también puede reactivarse")
        void cancelledCanBeReactivated() {
            Raffle raffle = raffle(1L, RaffleStatus.CANCELLED);
            raffle.setPrizes(List.of(new Prize()));
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            service.activateRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.ACTIVE);
        }

        @Test
        @DisplayName("status distinto de DRAFT/CANCELLED: lanza InvalidOperationException")
        void wrongStatus_throwsInvalidOperationException() {
            Raffle raffle = raffle(1L, RaffleStatus.COMPLETED);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("sin premios: lanza InvalidOperationException")
        void withoutPrizes_throwsInvalidOperationException() {
            Raffle raffle = raffle(1L, RaffleStatus.DRAFT);
            raffle.setPrizes(List.of());
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("ya expiró (endDate en el pasado): lanza InvalidOperationException")
        void expired_throwsInvalidOperationException() {
            Raffle raffle = raffle(1L, RaffleStatus.DRAFT);
            raffle.setPrizes(List.of(new Prize()));
            raffle.setEndDate(ZonedDateTime.now().minusDays(1));
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L)).isInstanceOf(InvalidOperationException.class);
        }
    }

    @Test
    @DisplayName("closeRaffle: ACTIVE pasa a CLOSED; cualquier otro status lanza InvalidOperationException")
    void closeRaffle_onlyFromActive() {
        Raffle active = raffle(1L, RaffleStatus.ACTIVE);
        when(raffleRepository.findById(1L)).thenReturn(Optional.of(active));
        service.closeRaffle(1L);
        assertThat(active.getRaffleStatus()).isEqualTo(RaffleStatus.CLOSED);

        Raffle draft = raffle(2L, RaffleStatus.DRAFT);
        when(raffleRepository.findById(2L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.closeRaffle(2L)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("deleteRaffle: solo permite borrar rifas en DRAFT")
    void deleteRaffle_onlyFromDraft() {
        Raffle draft = raffle(1L, RaffleStatus.DRAFT);
        when(raffleRepository.findById(1L)).thenReturn(Optional.of(draft));
        service.deleteRaffle(1L);
        verify(raffleRepository).delete(draft);

        Raffle active = raffle(2L, RaffleStatus.ACTIVE);
        when(raffleRepository.findById(2L)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service.deleteRaffle(2L)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("cancelRaffle: solo se puede invocar sobre una rifa ACTIVE")
    void cancelRaffle_onlyFromActive() {
        Raffle draft = raffle(1L, RaffleStatus.DRAFT);
        when(raffleRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.cancelRaffle(1L)).isInstanceOf(InvalidOperationException.class);
        verify(raffleRepository, never()).save(any());
    }

    // ─── Consultas ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRaffleById")
    class GetRaffleById {

        @Test
        @DisplayName("id inválido: lanza IllegalArgumentException")
        void invalidId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getRaffleById(0L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rifa inexistente: lanza ObjectNotFoundException")
        void notFound_throwsObjectNotFoundException() {
            when(raffleRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRaffleById(1L)).isInstanceOf(ObjectNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getMyRafflesByStatus / countMyRafflesByStatus")
    class MyRaffles {

        @Test
        @DisplayName("status distinto de ACTIVE/COMPLETED: lanza IllegalArgumentException")
        void invalidStatus_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getMyRafflesByStatus(1L, RaffleStatus.DRAFT,
                    org.springframework.data.domain.Pageable.unpaged()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.countMyRafflesByStatus(1L, RaffleStatus.CANCELLED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("consumerId inválido: lanza IllegalArgumentException")
        void invalidConsumerId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.countMyRafflesByStatus(0L, RaffleStatus.ACTIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── updateRaffle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateRaffle")
    class UpdateRaffle {

        private UpdateRaffleRequestDTO validRequest() {
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            return new UpdateRaffleRequestDTO("Nuevo título", "Nueva descripción", RaffleType.STANDARD, false,
                    start, start.plusDays(5), start.plusDays(6));
        }

        @Test
        @DisplayName("fechas válidas: actualiza los campos de la rifa")
        void validDates_updatesFields() {
            Raffle raffle = raffle(1L, RaffleStatus.DRAFT);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            service.updateRaffle(9L, 1L, validRequest());

            assertThat(raffle.getTitle()).isEqualTo("Nuevo título");
            assertThat(raffle.getModifiedBy()).isEqualTo(9L);
        }

        @Test
        @DisplayName("drawDate no es posterior a endDate: lanza InvalidRequestException")
        void drawDateNotAfterEndDate_throwsInvalidRequestException() {
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            UpdateRaffleRequestDTO request = new UpdateRaffleRequestDTO("t", "d", RaffleType.STANDARD, false,
                    start, start.plusDays(5), start.plusDays(5)); // drawDate == endDate

            assertThatThrownBy(() -> service.updateRaffle(9L, 1L, request))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("endDate no es posterior a startDate: lanza InvalidRequestException")
        void endDateNotAfterStartDate_throwsInvalidRequestException() {
            ZonedDateTime start = ZonedDateTime.now().plusDays(5);
            UpdateRaffleRequestDTO request = new UpdateRaffleRequestDTO("t", "d", RaffleType.STANDARD, false,
                    start, start, start.plusDays(6)); // endDate == startDate

            assertThatThrownBy(() -> service.updateRaffle(9L, 1L, request))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    // ─── prepareRaffleCreation / confirmRaffleCreation (validaciones) ──────────

    @Nested
    @DisplayName("prepareRaffleCreation")
    class PrepareRaffleCreation {

        private CreateRaffleRequestDTO baseRequest() {
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            CreatePrizeRequestDTO prize = new CreatePrizeRequestDTO("Prize", "desc", "brand",
                    BigDecimal.TEN, PrizeType.PHYSICAL, 1, 1, "code", "instructions");
            CreateRaffleRuleRequestDTO rule = new CreateRaffleRuleRequestDTO(1L, 100L);
            return new CreateRaffleRequestDTO("t", "d", RaffleType.STANDARD, start, start.plusDays(5),
                    start.plusDays(6), 100L, 10L, false, DrawMethod.SYSTEM_RANDOM, List.of(prize), List.of(rule),
                    "terms");
        }

        @Test
        @DisplayName("cantidad de imágenes de premio no coincide con la cantidad de premios: lanza InvalidRequestException")
        void mismatchedPrizeImageCount_throwsInvalidRequestException() {
            CreateRaffleRequestDTO request = baseRequest();
            FileUploadRequestDTO raffleImage = new FileUploadRequestDTO("r.jpg", "image/png", 1000L, null, null);

            assertThatThrownBy(() -> service.prepareRaffleCreation(1L, request, raffleImage, List.of())) // 0 imágenes, 1 premio
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("posiciones de premio duplicadas: lanza InvalidRequestException antes de tocar storage")
        void duplicatePrizePositions_throwsInvalidRequestException() {
            CreateRaffleRequestDTO request = baseRequest();
            CreatePrizeRequestDTO samePosition = new CreatePrizeRequestDTO("Prize2", "desc", "brand",
                    BigDecimal.TEN, PrizeType.PHYSICAL, 1, 1, "code2", "instructions"); // posición 1 repetida
            request.setPrizes(List.of(request.getPrizes().get(0), samePosition));
            FileUploadRequestDTO raffleImage = new FileUploadRequestDTO("r.jpg", "image/png", 1000L, null, null);
            List<FileUploadRequestDTO> prizeImages = List.of(raffleImage, raffleImage);

            assertThatThrownBy(() -> service.prepareRaffleCreation(1L, request, raffleImage, prizeImages))
                    .isInstanceOf(InvalidRequestException.class);

            verify(raffleImageAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("drawDate no posterior a endDate: lanza InvalidRequestException")
        void invalidDates_throwsInvalidRequestException() {
            CreateRaffleRequestDTO request = baseRequest();
            request.setDrawDate(request.getEndDate()); // no es posterior
            FileUploadRequestDTO raffleImage = new FileUploadRequestDTO("r.jpg", "image/png", 1000L, null, null);

            assertThatThrownBy(() -> service.prepareRaffleCreation(1L, request, raffleImage, List.of(raffleImage)))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }
}
