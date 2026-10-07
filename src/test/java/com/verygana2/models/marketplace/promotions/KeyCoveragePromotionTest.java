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
        @DisplayName("la garantía que vuelve al cierre descuenta lo que repuso KEYS_RESERVE")
        void returnableGuaranteeExcludesWhatWasDrawn() {
            KeyCoveragePromotion promotion = authorized();
            promotion.registerGuaranteeDrawn(150_000L);

            assertThat(promotion.returnableGuaranteeCents()).isEqualTo(850_000L);
        }

        @Test
        @DisplayName("la garantía no se devuelve con la promoción abierta ni con compras en curso")
        void guaranteeReturnRequiresEndedAndNothingReserved() {
            KeyCoveragePromotion promotion = authorized();
            assertThatThrownBy(() -> promotion.markGuaranteeReturned(END)).isInstanceOf(InvalidStatusException.class);

            promotion.reserve(100_000L);
            promotion.end();
            assertThatThrownBy(() -> promotion.markGuaranteeReturned(END)).isInstanceOf(InvalidStatusException.class);

            promotion.release(100_000L);
            promotion.markGuaranteeReturned(END);
            assertThat(promotion.hasGuarantee()).isFalse();
            assertThat(promotion.getStatus()).isEqualTo(KeyCoveragePromotionStatus.ENDED);
        }
    }
}
