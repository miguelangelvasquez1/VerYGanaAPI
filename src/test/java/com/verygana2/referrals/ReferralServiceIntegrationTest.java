package com.verygana2.referrals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.mappers.ReferralMapperImpl;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.services.referrals.ReferralServiceImpl;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Test de integración del sistema de referidos contra H2 (modo MySQL).
 * Usa el mapper real generado por MapStruct.
 */
@DataJpaTest(properties = {
        // Perfil vacío: evita cargar application-dev.yml (llaves RSA, R2, etc.)
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:referrals-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReferralServiceImpl.class, ReferralMapperImpl.class})
@DisplayName("ReferralService (integración H2)")
class ReferralServiceIntegrationTest {

    @Autowired EntityManager em;
    @Autowired ReferralServiceImpl referralService;

    @Test
    @DisplayName("generateUniqueCode no colisiona con códigos ya persistidos")
    void generatedCodeDoesNotCollideWithPersisted() {
        TestEntities.persistConsumer(em); // ocupa un código en la BD

        String code = referralService.generateUniqueCode(8);

        assertThat(code).hasSize(8);
        assertThat(em.createQuery(
                        "SELECT COUNT(c) FROM ConsumerDetails c WHERE c.referralCode = :code", Long.class)
                .setParameter("code", code)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("flujo completo: registro con código de referido y consulta de referidos")
    void fullReferralFlow() {
        // 1. Referidor existente en BD
        ConsumerDetails referrer = TestEntities.persistConsumer(em);
        String referrerEmail = referrer.getUser().getEmail();

        // 2. Nuevo usuario se registra con el código del referidor
        ConsumerDetails newcomer = TestEntities.newConsumer(em);
        referralService.prepareNewConsumer(
                newcomer.getUser(), newcomer, referrer.getReferralCode().toLowerCase());
        em.persist(newcomer);
        em.flush();
        em.clear();

        // 3. El vínculo quedó persistido
        ConsumerDetails reloaded = em.find(ConsumerDetails.class, newcomer.getId());
        assertThat(reloaded.getReferredBy().getId()).isEqualTo(referrer.getId());
        assertThat(reloaded.getReferralCode()).isNotBlank()
                .isNotEqualTo(referrer.getReferralCode());

        // 4. El referidor ve a su referido con los datos mapeados
        List<ReferralItemDTO> referrals = referralService.getReferralsByEmail(referrerEmail);

        assertThat(referrals).hasSize(1);
        ReferralItemDTO item = referrals.get(0);
        assertThat(item.email()).isEqualTo(newcomer.getUser().getEmail());
        assertThat(item.userName()).isEqualTo(newcomer.getUserName());
        assertThat(item.municipality()).isEqualTo("Armenia");
    }

    @Test
    @DisplayName("un consumer sin referidos retorna lista vacía")
    void consumerWithoutReferralsReturnsEmptyList() {
        ConsumerDetails lonely = TestEntities.persistConsumer(em);

        List<ReferralItemDTO> referrals =
                referralService.getReferralsByEmail(lonely.getUser().getEmail());

        assertThat(referrals).isEmpty();
    }

    @Test
    @DisplayName("código inválido en el registro lanza excepción")
    void invalidCodeOnRegistrationThrows() {
        ConsumerDetails newcomer = TestEntities.newConsumer(em);

        assertThatThrownBy(() -> referralService.prepareNewConsumer(
                newcomer.getUser(), newcomer, "ZZZZ9999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inválido");
    }
}
