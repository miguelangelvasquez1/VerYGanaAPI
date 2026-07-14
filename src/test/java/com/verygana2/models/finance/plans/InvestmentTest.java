package com.verygana2.models.finance.plans;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.finance.WompiTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests de la entidad {@link Investment}: confirmación del depósito cuando Wompi aprueba el pago. */
@DisplayName("Investment (entidad)")
class InvestmentTest {

    @Test
    @DisplayName("confirm: marca confirmed=true, vincula la WompiTransaction y sella confirmedAt")
    void confirm_marksConfirmedAndLinksTransaction() {
        Investment investment = Investment.builder().confirmed(false).build();
        WompiTransaction tx = WompiTransaction.builder().build();

        investment.confirm(tx);

        assertThat(investment.getConfirmed()).isTrue();
        assertThat(investment.getWompiTransaction()).isSameAs(tx);
        assertThat(investment.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("onCreate (hook @PrePersist): confirmed nace en false si viene null")
    void onCreate_defaultsConfirmedToFalse() {
        Investment investment = new Investment();

        investment.onCreate();

        assertThat(investment.getConfirmed()).isFalse();
        assertThat(investment.getCreatedAt()).isNotNull();
    }
}
