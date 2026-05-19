package com.verygana2.utils;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();

        // Orden importa: primero tablas sin dependencias externas
        populator.addScript(new ClassPathResource("db/seed/categories.sql"));
        populator.addScript(new ClassPathResource("db/seed/avatars.sql"));
        populator.addScript(new ClassPathResource("db/seed/departments.sql"));
        populator.addScript(new ClassPathResource("db/seed/municipalities.sql")); // depende de departamentos
        populator.addScript(new ClassPathResource("db/seed/users.sql"));

        // No detiene la app si un script falla (útil si algunos datos ya existen)
        // populator.setContinueOnError(true);

        DatabasePopulatorUtils.execute(populator, dataSource);
        log.info("Seeds ejecutados correctamente");
    }
}