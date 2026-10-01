package com.verygana2.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para WalletRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:wallet-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("WalletRepository (integración H2)")
class WalletRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private WalletRepository walletRepository;

    // ==================== HELPERS ====================

    /**
     * Persiste un wallet vía TestEntities y luego sobreescribe su status
     * directamente (TestEntities.persistWallet solo produce ACTIVE/EXHAUSTED
     * vía recalculateStatus()). Wallet no tiene @PreUpdate que pise status,
     * así que el segundo flush lo deja en el valor pedido.
     */
    private Wallet persistWalletWithStatus(CommercialDetails commercial, long balanceCents, WalletStatus status) {
        Wallet wallet = TestEntities.persistWallet(em, commercial, balanceCents);
        wallet.setStatus(status);
        em.flush();
        return wallet;
    }

    // ==================== findByCommercialId ====================

    @Nested
    @DisplayName("findByCommercialId")
    class FindByCommercialId {

        @Test
        @DisplayName("encuentra el wallet del comercial")
        void findsWalletForCommercial() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 100_000_00L);

            Optional<Wallet> found = walletRepository.findByCommercialId(commercial.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(wallet.getId());
        }

        @Test
        @DisplayName("retorna vacío si el comercial no tiene wallet")
        void returnsEmptyWhenCommercialHasNoWallet() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            Optional<Wallet> found = walletRepository.findByCommercialId(commercial.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByCommercialIdForUpdate ====================

    @Nested
    @DisplayName("findByCommercialIdForUpdate")
    class FindByCommercialIdForUpdate {

        @Test
        @DisplayName("recupera el wallet correcto bajo lock pesimista dentro de la transacción del test")
        void bringsCorrectWalletUnderPessimisticLock() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 50_000_00L);

            Optional<Wallet> found = walletRepository.findByCommercialIdForUpdate(commercial.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(wallet.getId());
            assertThat(found.get().getBalanceCents()).isEqualTo(50_000_00L);
        }

        @Test
        @DisplayName("retorna vacío si el comercial no tiene wallet")
        void returnsEmptyWhenCommercialHasNoWallet() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            Optional<Wallet> found = walletRepository.findByCommercialIdForUpdate(commercial.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== existsByCommercialId ====================

    @Nested
    @DisplayName("existsByCommercialId")
    class ExistsByCommercialId {

        @Test
        @DisplayName("retorna true si el comercial tiene wallet")
        void returnsTrueWhenCommercialHasWallet() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            TestEntities.persistWallet(em, commercial, 0L);

            boolean exists = walletRepository.existsByCommercialId(commercial.getId());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("retorna false si el comercial no tiene wallet")
        void returnsFalseWhenCommercialHasNoWallet() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            boolean exists = walletRepository.existsByCommercialId(commercial.getId());

            assertThat(exists).isFalse();
        }
    }

    // ==================== findByStatusIn ====================

    @Nested
    @DisplayName("findByStatusIn")
    class FindByStatusIn {

        @Test
        @DisplayName("trae solo los wallets cuyo status está en la lista pedida")
        void bringsOnlyWalletsWithStatusInList() {
            CommercialDetails commercialActive = TestEntities.persistCommercial(em);
            Wallet active = persistWalletWithStatus(commercialActive, 100_000_00L, WalletStatus.ACTIVE);

            CommercialDetails commercialExhausted = TestEntities.persistCommercial(em);
            Wallet exhausted = persistWalletWithStatus(commercialExhausted, 0L, WalletStatus.EXHAUSTED);

            CommercialDetails commercialInactive = TestEntities.persistCommercial(em);
            persistWalletWithStatus(commercialInactive, 0L, WalletStatus.INACTIVE);

            List<Wallet> found = walletRepository.findByStatusIn(List.of(WalletStatus.ACTIVE,
                    WalletStatus.EXHAUSTED));

            assertThat(found).extracting(Wallet::getId)
                    .containsExactlyInAnyOrder(active.getId(), exhausted.getId());
        }

        @Test
        @DisplayName("retorna lista vacía si ningún wallet tiene un status de la lista")
        void returnsEmptyListWhenNoWalletMatches() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            persistWalletWithStatus(commercial, 0L, WalletStatus.INACTIVE);

            List<Wallet> found = walletRepository.findByStatusIn(List.of(WalletStatus.ACTIVE));

            assertThat(found).isEmpty();
        }
    }
}
