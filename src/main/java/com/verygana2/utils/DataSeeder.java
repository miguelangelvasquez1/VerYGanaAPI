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
        loadBaseData(populator);
        loadGames(populator);
        loadTestEntities(populator);

        // No detiene la app si un script falla (útil si algunos datos ya existen)
        // populator.setContinueOnError(true);

        DatabasePopulatorUtils.execute(populator, dataSource);
        log.info("Seeds ejecutados correctamente");
    }

    private void loadBaseData(ResourceDatabasePopulator populator) {
        populator.addScript(new ClassPathResource("db/seed/categories.sql"));
        populator.addScript(new ClassPathResource("db/seed/avatars.sql"));
        populator.addScript(new ClassPathResource("db/seed/departments.sql"));
        populator.addScript(new ClassPathResource("db/seed/municipalities.sql")); // depende de departamentos
        populator.addScript(new ClassPathResource("db/seed/productCategories.sql"));
    }

    private void loadTestEntities(ResourceDatabasePopulator populator) {
        populator.addScript(new ClassPathResource("db/seed/test-users.sql"));
        populator.addScript(new ClassPathResource("db/seed/test-campaigns.sql"));
        populator.addScript(new ClassPathResource("db/seed/test-productCategoryImageAsset.sql"));
        populator.addScript(new ClassPathResource("db/seed/test-productImageAsset.sql"));
        populator.addScript(new ClassPathResource("db/seed/test-products.sql"));
        populator.addScript(new ClassPathResource("db/seed/test-productStock.sql"));
    }

    private void loadGames(ResourceDatabasePopulator populator) {
        populator.addScript(new ClassPathResource("db/seed/games/cali/avoid-the-bomb.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/ball-bounce.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/balloon-lift.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/catch-it.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/hangman.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/match3.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/memory.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/sudoku.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/tap-to-rotate.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/cali/whack-a-mole.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/dash-runner.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/endless-runner.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/memory-match.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/mini-flappy.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/simple-crossword.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/stack-tower.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/tic-tac-toe.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/tile-puzzle.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/trivia-quiz.sql"));
        populator.addScript(new ClassPathResource("db/seed/games/bogota/word-search.sql"));
    }


}