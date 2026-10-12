package com.verygana2.models.marketplace.promotions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion.Terms;

@DisplayName("KeyCoveragePromotion")
class KeyCoveragePromotionTest {

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END = START.plusDays(10);

    private static Terms terms(int pct) {
        return new Terms("Semana del 70", pct, START, END, 1_000_000L, null, "Términos");
    }

    private static KeyCoveragePromotion authorized() {
        KeyCoveragePromotion promotion = KeyCoveragePromotion.draft(terms(70));
        promotion.authorize(1L, START.minusDays(1));
        return promotion;
    }

    @Nested
    @DisplayName("porcentaje especial")
    class CoveragePct {

        @ParameterizedTest(name = "{0} % es válido")
        @ValueSource(ints = { 51, 60, 70 })
        @DisplayName("entre 51 y 70 se acepta")
        void acceptsSpecialRange(int pct) {
            assertThat(KeyCoveragePromotion.draft(terms(pct)).getCoveragePct()).isEqualTo(pct);
        }

        @ParameterizedTest(name = "{0} % se rechaza")
        @ValueSource(ints = { 0, 50, 71, 100 })
        @DisplayName("50 no es especial y 71 supera el máximo del contrato")
        void rejectsOutsideSpecialRange(int pct) {
            assertThatThrownBy(() -> KeyCoveragePromotion.draft(terms(pct))).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("no puede ser permanente: la fecha de fin es obligatoria y posterior al inicio")
    void requiresEndAfterStart() {
        assertThatThrownBy(() -> KeyCoveragePromotion.draft(
                new Terms("Sin fin", 70, START, null, 1_000_000L, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> KeyCoveragePromotion.draft(
                new Terms("Al revés", 70, END, START, 1_000_000L, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Nested
    @DisplayName("vigencia")
    class Liveness {

        @Test
        @DisplayName("un borrador no aplica aunque esté dentro de fechas: no es automática")
        void draftIsNotLive() {
            assertThat(KeyCoveragePromotion.draft(terms(70)).isLiveAt(START.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("autorizada aplica solo dentro de su vigencia")
        void authorizedIsLiveOnlyWithinDates() {
            KeyCoveragePromotion promotion = authorized();

            assertThat(promotion.isLiveAt(START.minusSeconds(1))).isFalse();
            assertThat(promotion.isLiveAt(START)).isTrue();
            assertThat(promotion.isLiveAt(END.minusSeconds(1))).isTrue();
            assertThat(promotion.isLiveAt(END)).isFalse();
        }

        @Test
        @DisplayName("pausada deja de aplicar y reanudada vuelve; cerrada no vuelve")
        void pauseResumeEnd() {
            KeyCoveragePromotion promotion = authorized();
            ZonedDateTime during = START.plusDays(1);

            promotion.pause();
            assertThat(promotion.isLiveAt(during)).isFalse();
            promotion.resume();
            assertThat(promotion.isLiveAt(during)).isTrue();
            promotion.end();
            assertThat(promotion.isLiveAt(during)).isFalse();
            assertThatThrownBy(promotion::resume).isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("no se autoriza una promoción cuya vigencia ya terminó")
        void cannotAuthorizeAfterEnd() {
            KeyCoveragePromotion promotion = KeyCoveragePromotion.draft(terms(70));

            assertThatThrownBy(() -> promotion.authorize(1L, END.plusSeconds(1))).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("lo autorizado no se edita: cada edición del borrador sube la versión y ahí queda")
    void authorizedCannotBeEdited() {
        KeyCoveragePromotion promotion = KeyCoveragePromotion.draft(terms(60));
        promotion.updateDraft(terms(70));
        assertThat(promotion.getConfigVersion()).isEqualTo(2);

        promotion.authorize(1L, START.minusDays(1));

        assertThatThrownBy(() -> promotion.updateDraft(terms(65))).isInstanceOf(InvalidStatusException.class);
        assertThat(promotion.getCoveragePct()).isEqualTo(70);
    }

    @Nested
    @DisplayName("presupuesto")
    class Budget {

        @Test
        @DisplayName("reserva, confirma y libera sin perder ni duplicar centavos")
        void reserveConfirmRelease() {
            KeyCoveragePromotion promotion = authorized();

            promotion.reserve(300_000L);
            promotion.reserve(200_000L);
            assertThat(promotion.availableBudgetCents()).isEqualTo(500_000L);

            promotion.confirm(300_000L);
            promotion.release(200_000L);

            assertThat(promotion.getConsumedCents()).isEqualTo(300_000L);
            assertThat(promotion.getReservedCents()).isZero();
            assertThat(promotion.availableBudgetCents()).isEqualTo(700_000L);
        }

        @Test
        @DisplayName("agotamiento: no reserva más de lo disponible")
        void cannotReserveBeyondBudget() {
            KeyCoveragePromotion promotion = authorized();
            promotion.reserve(1_000_000L);

            assertThatThrownBy(() -> promotion.reserve(1L)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("deja de cubrir un producto cuando lo que queda no alcanza para el margen de una unidad")
        void canCoverOneUnitUntilTheBudgetRunsOut() {
            KeyCoveragePromotion promotion = authorized(); // 70 %, presupuesto $10.000
            // Producto de $40.000 con tope ordinario 50 %: el margen de una unidad son $8.000.
            assertThat(promotion.canCoverOneUnit(4_000_000L, 50, 1_000L)).isTrue();

            promotion.reserve(300_000L); // quedan $7.000
            assertThat(promotion.canCoverOneUnit(4_000_000L, 50, 1_000L)).isFalse();
            // Un producto más barato todavía cabe: su margen son $4.000.
            assertThat(promotion.canCoverOneUnit(2_000_000L, 50, 1_000L)).isTrue();
        }

        @Test
        @DisplayName("al cierre vuelve lo no comprometido: ni lo que el Fondo ya puso ni lo reservado por compras en curso")
        void releasableExcludesFundedAndReserved() {
            KeyCoveragePromotion promotion = authorized(); // presupuesto $10.000
            promotion.registerGuaranteeDrawn(150_000L);
            promotion.reserve(100_000L);

            assertThat(promotion.releasableGuaranteeCents()).isEqualTo(750_000L);
        }

        @Test
        @DisplayName("con una compra en curso se devuelve por partes: primero lo libre, y se salda cuando el pago se resuelve")
        void guaranteeIsReturnedInParts() {
            KeyCoveragePromotion promotion = authorized();
            promotion.reserve(100_000L);
            promotion.end();

            promotion.registerGuaranteeReturned(promotion.releasableGuaranteeCents(), END);
            assertThat(promotion.getGuaranteeReturnedCents()).isEqualTo(900_000L);
            assertThat(promotion.hasGuarantee()).as("todavía retiene lo de la compra en curso").isTrue();
            assertThat(promotion.releasableGuaranteeCents()).isZero();

            promotion.release(100_000L); // el pago falló
            promotion.registerGuaranteeReturned(promotion.releasableGuaranteeCents(), END);

            assertThat(promotion.getGuaranteeReturnedCents()).isEqualTo(1_000_000L);
            assertThat(promotion.hasGuarantee()).isFalse();
            assertThat(promotion.getStatus()).isEqualTo(KeyCoveragePromotionStatus.ENDED);
        }

        @Test
        @DisplayName("el fondo no se devuelve con la promoción abierta")
        void guaranteeIsNotReturnedWhileRunning() {
            KeyCoveragePromotion promotion = authorized();

            assertThatThrownBy(() -> promotion.registerGuaranteeReturned(1L, END))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("el límite por usuario no puede superar el presupuesto")
        void perUserLimitCannotExceedBudget() {
            assertThatThrownBy(() -> KeyCoveragePromotion.draft(
                    new Terms("Límite mayor", 70, START, END, 1_000_000L, 1_000_001L, null)))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
