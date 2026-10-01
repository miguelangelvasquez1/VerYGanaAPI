package com.verygana2.controllers.admin;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutAdminController} (clase anotada con
 * {@code @PreAuthorize("hasRole('ADMIN')")} — no verificado aquí, cubierto por
 * el test de integración correspondiente). Cada endpoint delega directamente
 * en {@link PayoutService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutAdminController")
class PayoutAdminControllerTest {

    @Mock private PayoutService payoutService;

    private PayoutAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PayoutAdminController(payoutService);
    }

    @Nested
    @DisplayName("GET / (getPayoutsForDate)")
    class GetPayoutsForDate {

        @Test
        @DisplayName("con fecha explícita, delega en el service con esa fecha")
        void getPayoutsForDate_withExplicitDate() {
            LocalDate date = LocalDate.of(2025, 1, 15);
            PayoutResponseDTO payout = mock(PayoutResponseDTO.class);
            List<PayoutResponseDTO> expected = List.of(payout);
            when(payoutService.getPayoutsForDate(date)).thenReturn(expected);

            var response = controller.getPayoutsForDate(date);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
            verify(payoutService).getPayoutsForDate(date);
        }

        @Test
        @DisplayName("sin fecha, usa el día de hoy")
        void getPayoutsForDate_withoutDate_usesToday() {
            List<PayoutResponseDTO> expected = List.of();
            when(payoutService.getPayoutsForDate(LocalDate.now())).thenReturn(expected);

            var response = controller.getPayoutsForDate(null);

            assertThat(response.getBody()).isSameAs(expected);
            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(payoutService).getPayoutsForDate(captor.capture());
            assertThat(captor.getValue()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    @DisplayName("POST /run-now")
    class RunNow {

        @Test
        @DisplayName("dispara scheduleDailyPayouts seguido de processScheduledPayouts, con periodo de 1 día")
        void runNow_schedulesAndProcesses() {
            var response = controller.runNow();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<ZonedDateTime> startCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            ArgumentCaptor<ZonedDateTime> endCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(payoutService).scheduleDailyPayouts(startCaptor.capture(), endCaptor.capture());
            verify(payoutService).processScheduledPayouts();

            assertThat(startCaptor.getValue()).isEqualTo(endCaptor.getValue().minusDays(1));
        }
    }

    @Nested
    @DisplayName("POST /retry-now")
    class RetryNow {

        @Test
        @DisplayName("delega en retryFailedPayouts")
        void retryNow_delegates() {
            var response = controller.retryNow();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payoutService).retryFailedPayouts();
        }
    }

    @Nested
    @DisplayName("GET /{id}/wompi-status")
    class GetWompiStatus {

        @Test
        @DisplayName("delega en el service con el id del path")
        void getWompiStatus_delegates() {
            UUID id = UUID.randomUUID();
            Map<String, Object> expected = Map.of("status", "APPROVED");
            when(payoutService.getWompiStatus(id)).thenReturn(expected);

            var response = controller.getWompiStatus(id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }
    }
}
