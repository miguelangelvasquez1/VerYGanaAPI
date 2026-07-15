package com.verygana2.models.pqrs;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.userDetails.AdminDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link Pqrs}: las reglas de transición de estado
 * (¿puede revisarse?, ¿puede resolverse?), la generación del número de
 * radicado y el estado inicial que asigna {@code onCreate()} según si
 * ya viene con un admin asignado o no. El test está en el mismo paquete
 * que la clase para poder invocar directamente los hooks JPA
 * package-private ({@code onCreate}/{@code onUpdate}) sin levantar Hibernate.
 */
@DisplayName("Pqrs (entidad)")
class PqrsTest {

    @Nested
    @DisplayName("canBeReviewed")
    class CanBeReviewed {

        @Test
        @DisplayName("true solo cuando el status es RECIBIDA")
        void trueOnlyWhenReceived() {
            assertThat(pqrsWithStatus(PqrsStatus.RECIBIDA).canBeReviewed()).isTrue();
        }

        @Test
        @DisplayName("false en cualquier otro status")
        void falseForOtherStatuses() {
            assertThat(pqrsWithStatus(PqrsStatus.PENDIENTE_ASIGNACION).canBeReviewed()).isFalse();
            assertThat(pqrsWithStatus(PqrsStatus.EN_REVISION).canBeReviewed()).isFalse();
            assertThat(pqrsWithStatus(PqrsStatus.RESUELTA).canBeReviewed()).isFalse();
            assertThat(pqrsWithStatus(PqrsStatus.CERRADA).canBeReviewed()).isFalse();
        }
    }

    @Nested
    @DisplayName("canBeResolved")
    class CanBeResolved {

        @Test
        @DisplayName("true para RECIBIDA y EN_REVISION")
        void trueForReceivedAndUnderReview() {
            assertThat(pqrsWithStatus(PqrsStatus.RECIBIDA).canBeResolved()).isTrue();
            assertThat(pqrsWithStatus(PqrsStatus.EN_REVISION).canBeResolved()).isTrue();
        }

        @Test
        @DisplayName("false para PENDIENTE_ASIGNACION, RESUELTA y CERRADA")
        void falseForOtherStatuses() {
            assertThat(pqrsWithStatus(PqrsStatus.PENDIENTE_ASIGNACION).canBeResolved()).isFalse();
            assertThat(pqrsWithStatus(PqrsStatus.RESUELTA).canBeResolved()).isFalse();
            assertThat(pqrsWithStatus(PqrsStatus.CERRADA).canBeResolved()).isFalse();
        }
    }

    @Nested
    @DisplayName("getBased")
    class GetBased {

        @Test
        @DisplayName("sin id (aún no persistido): retorna null")
        void withoutId_returnsNull() {
            Pqrs pqrs = Pqrs.builder().build();

            assertThat(pqrs.getBased()).isNull();
        }

        @Test
        @DisplayName("con id y createdAt: formatea PQRS-<año>-<id con 6 dígitos>")
        void withIdAndCreatedAt_formatsRadicado() {
            Pqrs pqrs = Pqrs.builder()
                    .id(42L)
                    .createdAt(ZonedDateTime.now().withYear(2026))
                    .build();

            assertThat(pqrs.getBased()).isEqualTo("PQRS-2026-000042");
        }

        @Test
        @DisplayName("con id pero sin createdAt: usa el año actual como respaldo")
        void withIdButNoCreatedAt_fallsBackToCurrentYear() {
            Pqrs pqrs = Pqrs.builder().id(7L).build();

            assertThat(pqrs.getBased()).isEqualTo("PQRS-" + ZonedDateTime.now().getYear() + "-000007");
        }
    }

    @Nested
    @DisplayName("onCreate (hook @PrePersist)")
    class OnCreateHook {

        @Test
        @DisplayName("con admin ya asignado: el status inicial es RECIBIDA")
        void withAssignedAdmin_defaultsToReceived() {
            Pqrs pqrs = Pqrs.builder().assignedAdmin(new AdminDetails()).build();

            pqrs.onCreate();

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.RECIBIDA);
            assertThat(pqrs.getCreatedAt()).isNotNull();
            assertThat(pqrs.getUpdatedAt()).isEqualTo(pqrs.getCreatedAt());
        }

        @Test
        @DisplayName("sin admin asignado: el status inicial es PENDIENTE_ASIGNACION")
        void withoutAssignedAdmin_defaultsToPendingAssignment() {
            Pqrs pqrs = Pqrs.builder().build();

            pqrs.onCreate();

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.PENDIENTE_ASIGNACION);
        }

        @Test
        @DisplayName("si ya trae un status explícito, no lo sobrescribe")
        void withExplicitStatus_doesNotOverrideIt() {
            Pqrs pqrs = Pqrs.builder().status(PqrsStatus.EN_REVISION).build();

            pqrs.onCreate();

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.EN_REVISION);
        }
    }

    @Test
    @DisplayName("onUpdate (hook @PreUpdate): refresca updatedAt")
    void onUpdate_refreshesUpdatedAt() {
        Pqrs pqrs = Pqrs.builder().updatedAt(ZonedDateTime.now().minusDays(5)).build();
        ZonedDateTime staleUpdatedAt = pqrs.getUpdatedAt();

        pqrs.onUpdate();

        assertThat(pqrs.getUpdatedAt()).isAfter(staleUpdatedAt);
    }

    private Pqrs pqrsWithStatus(PqrsStatus status) {
        return Pqrs.builder().status(status).build();
    }
}
