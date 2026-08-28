package com.verygana2.mappers.finance;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link MoneyMapper}: conversión entre {@link BigDecimal} (pesos) y
 * {@link Long} (centavos). Es una clase manual (no MapStruct), se instancia
 * directamente.
 */
@DisplayName("MoneyMapper")
class MoneyMapperTest {

    private final MoneyMapper mapper = new MoneyMapper();

    @Nested
    @DisplayName("toCents(BigDecimal)")
    class ToCents {

        @Test
        @DisplayName("valor positivo típico: 100.00 -> 10000L")
        void typicalPositiveValue() {
            assertThat(mapper.toCents(new BigDecimal("100.00"))).isEqualTo(10000L);
        }

        @Test
        @DisplayName("cero: 0 -> 0L")
        void zero() {
            assertThat(mapper.toCents(BigDecimal.ZERO)).isEqualTo(0L);
        }

        @Test
        @DisplayName("negativo: -50.25 -> -5025L")
        void negativeValue() {
            assertThat(mapper.toCents(new BigDecimal("-50.25"))).isEqualTo(-5025L);
        }

        @Test
        @DisplayName("decimales con más de 2 dígitos: TRUNCA, no redondea (usa longValue() sin RoundingMode)")
        void extraDecimals_areTruncatedNotRounded() {
            // 10.567 * 100 = 1056.7 -> longValue() trunca la parte fraccionaria -> 1056, no 1057
            assertThat(mapper.toCents(new BigDecimal("10.567"))).isEqualTo(1056L);
        }

        @Test
        @DisplayName("null: lanza NullPointerException (comportamiento actual, no corregido)")
        void nullAmount_throwsNPE() {
            assertThatThrownBy(() -> mapper.toCents(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("fromCents(Long)")
    class FromCents {

        @Test
        @DisplayName("valor típico: 10000L -> 100.00")
        void typicalValue() {
            assertThat(mapper.fromCents(10000L)).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("cero: 0L -> 0")
        void zero() {
            assertThat(mapper.fromCents(0L)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("negativo: -5025L -> -50.25")
        void negativeValue() {
            assertThat(mapper.fromCents(-5025L)).isEqualByComparingTo("-50.25");
        }

        @Test
        @DisplayName("null: lanza NullPointerException (unboxing de Long null a long en BigDecimal.valueOf)")
        void nullCents_throwsNPE() {
            assertThatThrownBy(() -> mapper.fromCents(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("cociente no exacto en base 10: NO ocurre con divisor 100 (2^2*5^2 divide toda potencia de 10), "
                + "por lo tanto divide() sin escala nunca lanza ArithmeticException para ningún Long")
        void quotientAlwaysExact_neverThrowsArithmeticException() {
            // 100 solo tiene como factores primos 2 y 5 (los mismos que la base 10),
            // así que cualquier entero dividido entre 100 termina en una cantidad finita
            // de decimales (a lo sumo 2). divide() sin RoundingMode nunca falla aquí.
            assertThat(mapper.fromCents(1L)).isEqualByComparingTo("0.01");
            assertThat(mapper.fromCents(333L)).isEqualByComparingTo("3.33");
            assertThat(mapper.fromCents(Long.MAX_VALUE)).isNotNull();
        }
    }
}
