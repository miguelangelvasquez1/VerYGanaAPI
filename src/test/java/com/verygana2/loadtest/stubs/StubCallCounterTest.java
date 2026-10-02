package com.verygana2.loadtest.stubs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("StubCallCounter - contador visible desde el arranque")
class StubCallCounterTest {

    @Test
    @DisplayName("Los tres proveedores aparecen en 0 antes de la primera llamada")
    void registersEveryProviderAtZeroOnStartup() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new StubCallCounter(registry);

        for (String provider : new String[] {
                StubCallCounter.EMAIL, StubCallCounter.SMS, StubCallCounter.RECAPTCHA }) {
            var counter = registry.find("loadtest.stub.calls").tag("provider", provider).counter();
            assertThat(counter).as("contador de %s", provider).isNotNull();
            assertThat(counter.count()).isZero();
        }
    }

    @Test
    @DisplayName("count suma sobre el contador ya registrado")
    void countIncrementsTheProviderCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StubCallCounter counter = new StubCallCounter(registry);

        counter.count(StubCallCounter.EMAIL);
        counter.count(StubCallCounter.EMAIL);

        assertThat(registry.get("loadtest.stub.calls").tag("provider", "email").counter().count())
                .isEqualTo(2.0);
    }
}
