package com.verygana2.utils;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.raffles.Prize;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.security.ClaimCodeEncryptor;
import com.verygana2.security.ProductCodeEncryptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds de referencia completos y datos de demo asociados a los usuarios estables
 * de beta_users.sql.
 *
 * Separado de {@link DataSeeder} (@Profile dev) a propósito: la demo no debe
 * mezclarse con los datos de desarrollo (test-users.sql, test-campaigns.sql,
 * etc.). Solo corre con el perfil de Spring "beta" o "demo" activo — nunca
 * en "prod". PlanDataInitializer (sin @Profile, @Order(2)) ya deja creados
 * los 3 planes (BASIC/STANDARD/PREMIUM) antes de que este runner se
 * ejecute, así que beta_users.sql puede referenciarlos por code.
 */
@Component
@Profile({ "beta", "demo" })
@RequiredArgsConstructor
@Slf4j
public class BetaDataSeeder implements CommandLineRunner {

    private final DataSource dataSource;
    private final ProductStockRepository productStockRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PrizeRepository prizeRepository;
    private final ProductCodeEncryptor productCodeEncryptor;
    private final ClaimCodeEncryptor claimCodeEncryptor;
    private final PasswordEncoder passwordEncoder;

    private static final String RAW_CODE_PREFIX = "RAW:";

    @Override
    public void run(String... args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        seedScripts().forEach(path -> populator.addScript(new ClassPathResource(path)));

        DatabasePopulatorUtils.execute(populator, dataSource);

        encryptSeedCodes();
        log.info("Seed beta ejecutado correctamente");
    }

    static List<String> seedScripts() {
        List<String> scripts = new ArrayList<>(ReferenceSeedScripts.all());
        scripts.remove(ReferenceSeedScripts.PET_COMMERCIAL_ITEMS);
        scripts.addAll(List.of(
                "db/seed/beta/beta_users.sql",
                "db/seed/beta/beta_campaigns_surveys.sql",
                "db/seed/beta/beta_raffles_marketplace.sql",
                "db/seed/beta/beta_finance.sql",
                ReferenceSeedScripts.PET_COMMERCIAL_ITEMS,
                "db/seed/beta/beta_pqrs_notifications.sql"));
        return List.copyOf(scripts);
    }

    private void encryptSeedCodes() {
        int stockCodes = 0;
        for (ProductStock stock : productStockRepository.findAll()) {
            if (stock.getCode() != null && stock.getCode().startsWith(RAW_CODE_PREFIX)) {
                String plainCode = stock.getCode().substring(RAW_CODE_PREFIX.length());
                stock.setCode(productCodeEncryptor.encrypt(plainCode));
                stock.setCodeHash(productCodeEncryptor.hash(plainCode));
                productStockRepository.save(stock);
                stockCodes++;
            }
        }

        int deliveredCodes = 0;
        int claimPins = 0;
        for (PurchaseItem item : purchaseItemRepository.findAll()) {
            boolean changed = false;
            if (item.getDeliveredCode() != null && item.getDeliveredCode().startsWith(RAW_CODE_PREFIX)) {
                String plainCode = item.getDeliveredCode().substring(RAW_CODE_PREFIX.length());
                item.setDeliveredCode(productCodeEncryptor.encrypt(plainCode));
                deliveredCodes++;
                changed = true;
            }
            if (item.getClaimPinHash() != null && item.getClaimPinHash().startsWith(RAW_CODE_PREFIX)) {
                String plainPin = item.getClaimPinHash().substring(RAW_CODE_PREFIX.length());
                item.setClaimPinHash(passwordEncoder.encode(plainPin));
                claimPins++;
                changed = true;
            }
            if (changed) {
                purchaseItemRepository.save(item);
            }
        }

        int prizeCodes = 0;
        for (Prize prize : prizeRepository.findAll()) {
            if (prize.getClaimCode() != null && prize.getClaimCode().startsWith(RAW_CODE_PREFIX)) {
                String plainCode = prize.getClaimCode().substring(RAW_CODE_PREFIX.length());
                prize.setClaimCode(claimCodeEncryptor.encrypt(plainCode));
                prizeRepository.save(prize);
                prizeCodes++;
            }
        }

        log.info("Códigos demo beta protegidos: inventario={}, entregas={}, PINes={}, premios={}",
                stockCodes, deliveredCodes, claimPins, prizeCodes);
    }
}
