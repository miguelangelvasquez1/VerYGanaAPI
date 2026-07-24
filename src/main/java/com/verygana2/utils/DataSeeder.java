package com.verygana2.utils;

import java.time.ZonedDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.security.ProductCodeEncryptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final DataSource dataSource;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductCodeEncryptor productCodeEncryptor;

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

        // product_stock.code va cifrado (ver CodeEncryptor), por lo que no se puede
        // sembrar con un script SQL crudo: se hace vía JPA para que quede cifrado
        // y con su code_hash igual que si lo hubiera creado un commercial real.
        seedProductStock();

        log.info("Seeds ejecutados correctamente");
    }

    private record SeedCode(Long productId, String code) {
    }

    private void seedProductStock() {
        List<SeedCode> seedCodes = List.of(
                new SeedCode(1L, "PLAY-YUHJ"),
                new SeedCode(1L, "PLAY-43HM"),
                new SeedCode(1L, "PLAY-TRFB"),
                new SeedCode(2L, "SPOTIFY-HJLK"),
                new SeedCode(2L, "SPOTIFY-HPOY"),
                new SeedCode(2L, "SPOTIFY-5RTH"),
                new SeedCode(3L, "NETFLIX-GHMK"),
                new SeedCode(3L, "NETFLIX-ADSC"),
                new SeedCode(3L, "NETFLIX-YU78"),
                new SeedCode(4L, "ARA-32FB"),
                new SeedCode(4L, "ARA-54FC"),
                new SeedCode(4L, "ARA-78KL"),
                new SeedCode(5L, "SMART-56GH"),
                new SeedCode(5L, "SMART-TGKM"),
                new SeedCode(5L, "SMART-S3DF"));

        for (SeedCode seed : seedCodes) {
            String codeHash = productCodeEncryptor.hash(seed.code());

            if (productStockRepository.existsByProductIdAndCodeHash(seed.productId(), codeHash)) {
                continue;
            }

            Product product = productRepository.findById(seed.productId()).orElse(null);
            if (product == null) {
                log.warn("No se pudo sembrar product_stock: producto {} no existe", seed.productId());
                continue;
            }

            ProductStock stock = ProductStock.builder()
                    .product(product)
                    .code(productCodeEncryptor.encrypt(seed.code()))
                    .codeHash(codeHash)
                    .status(StockStatus.AVAILABLE)
                    .createdAt(ZonedDateTime.now())
                    .build();

            productStockRepository.save(stock);
        }
    }

    private void loadBaseData(ResourceDatabasePopulator populator) {
        populator.addScript(new ClassPathResource("db/seed/categories.sql"));
        populator.addScript(new ClassPathResource("db/seed/system-features.sql"));
        populator.addScript(new ClassPathResource("db/seed/pricing-config.sql"));
        populator.addScript(new ClassPathResource("db/seed/legal-documents.sql"));
        populator.addScript(new ClassPathResource("db/seed/avatars.sql"));
        populator.addScript(new ClassPathResource("db/seed/departments.sql"));
        populator.addScript(new ClassPathResource("db/seed/municipalities.sql")); // depende de departamentos
        populator.addScript(new ClassPathResource("db/seed/productCategories.sql"));
    }

    private void loadTestEntities(ResourceDatabasePopulator populator) {
        populator.addScript(new ClassPathResource("db/seed/test/test-users.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-level-users.sql"));
        // populator.addScript(new ClassPathResource("db/seed/test/test-surveys.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-campaigns.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-products.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-productCategoryImageAsset.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-productImageAsset.sql"));
        populator.addScript(new ClassPathResource("db/seed/test/test-raffle-referral-rule.sql")); // depende de test-users (admin)
        // populator.addScript(new ClassPathResource("db/seed/test/test-ads.sql")); // depende de test-users (comercial)
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