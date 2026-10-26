package com.verygana2.config.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import com.verygana2.services.interfaces.marketplace.ProductImageRepairService;
import com.verygana2.services.interfaces.marketplace.ProductImageRepairService.ProductImageRepairReport;

@DisplayName("ProductImageRepairRunner: solo existe con la bandera encendida")
class ProductImageRepairRunnerConditionTest {

    private final ProductImageRepairService service = mock(ProductImageRepairService.class);

    private ApplicationContextRunner runner() {
        when(service.repairActiveProductImages())
                .thenReturn(new ProductImageRepairReport(0, 0, 0, List.of(), List.of()));
        return new ApplicationContextRunner()
                .withBean(ProductImageRepairService.class, () -> service)
                .withUserConfiguration(ProductImageRepairRunner.class);
    }

    @Test
    @DisplayName("sin la clave, el bean no existe")
    void notCreatedWithoutFlag() {
        runner().run(ctx -> assertThat(ctx).doesNotHaveBean(ProductImageRepairRunner.class));
    }

    @Test
    @DisplayName("con la bandera en false, el bean no existe")
    void notCreatedWhenFlagFalse() {
        runner().withPropertyValues("maintenance.product-image-repair.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ProductImageRepairRunner.class));
    }

    @Test
    @DisplayName("con la bandera en true, el bean existe")
    void createdWhenFlagTrue() {
        runner().withPropertyValues("maintenance.product-image-repair.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ProductImageRepairRunner.class));
    }

    @Test
    @DisplayName("al estar la aplicación lista corre la reparación una vez, sin esperar al arranque")
    void runsRepairOnceOnApplicationReady() {
        runner().withPropertyValues("maintenance.product-image-repair.enabled=true").run(ctx -> {
            // El contexto arrancó y la reparación aún no corrió: depende del evento.
            verify(service, times(0)).repairActiveProductImages();

            ctx.publishEvent(mock(ApplicationReadyEvent.class));

            verify(service, timeout(2000).times(1)).repairActiveProductImages();
        });
    }
}
