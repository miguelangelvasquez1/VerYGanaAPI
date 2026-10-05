package com.verygana2.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.StandardEnvironment;

@DisplayName("Paquete loadtest - todo bean exige el perfil loadtest")
class LoadTestPackageGatingTest {

    private static final String PACKAGE = "com.verygana2.loadtest";

    @Test
    @DisplayName("cada @Component/@Service/@Configuration del paquete lleva @Profile(\"loadtest\")")
    void everyBeanInLoadTestPackageRequiresLoadTestProfile() {
        // Con el perfil loadtest activo el escaneo ve todos los beans del paquete...
        List<String> all = scan("loadtest");
        // ...y sin él (dev, prod, beta) no debe ver ninguno: el escaneo evalúa @Profile.
        List<String> activeWithoutLoadTest = scan("prod", "beta");
        List<String> activeWithDev = scan("dev");

        assertThat(all).as("el escaneo no encontró el paquete").isNotEmpty();
        assertThat(activeWithoutLoadTest)
                .as("Beans del paquete loadtest que existirían en prod+beta (falta @Profile)")
                .isEmpty();
        assertThat(activeWithDev)
                .as("Beans del paquete loadtest que existirían en dev (falta @Profile)")
                .isEmpty();
    }

    private static List<String> scan(String... activeProfiles) {
        // Filtros por defecto: @Component y sus meta-anotaciones (@Service, @Configuration...).
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        scanner.setEnvironment(environment);
        return scanner.findCandidateComponents(PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .toList();
    }
}
