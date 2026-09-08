package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para KeyWalletRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-wallet-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KeyWalletRepository (integración H2)")
class KeyWalletRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private KeyWalletRepository keyWalletRepository;

    // ==================== HELPERS ====================

    private KeyWallet persistKeyWallet(ConsumerDetails consumer) {
        KeyWallet keyWallet = KeyWallet.createFor(consumer);
        em.persist(keyWallet);
        em.flush();
        return keyWallet;
    }

    // ==================== findByConsumerId ====================

    @Nested
    @DisplayName("findByConsumerId")
    class FindByConsumerId {

        @Test
        @DisplayName("trae el key wallet del consumer")
        void returnsKeyWalletForConsumer() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet keyWallet = persistKeyWallet(consumer);

            Optional<KeyWallet> found = keyWalletRepository.findByConsumerId(consumer.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(keyWallet.getId());
        }

        @Test
        @DisplayName("vacío cuando el consumer no tiene key wallet")
        void emptyWhenConsumerHasNoKeyWallet() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);

            Optional<KeyWallet> found = keyWalletRepository.findByConsumerId(consumer.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== existsByConsumerId ====================

    @Nested
    @DisplayName("existsByConsumerId")
    class ExistsByConsumerId {

        @Test
        @DisplayName("true cuando el consumer tiene key wallet")
        void trueWhenConsumerHasKeyWallet() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            persistKeyWallet(consumer);

            assertThat(keyWalletRepository.existsByConsumerId(consumer.getId())).isTrue();
        }

        @Test
        @DisplayName("false cuando el consumer no tiene key wallet")
        void falseWhenConsumerHasNoKeyWallet() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);

            assertThat(keyWalletRepository.existsByConsumerId(consumer.getId())).isFalse();
        }
    }
}
