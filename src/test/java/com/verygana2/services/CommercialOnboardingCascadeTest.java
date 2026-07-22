package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.EntityManager;

/**
 * Reproduce el grafo que arma UserServiceImpl.registerCommercial()
 * (User -> CommercialDetails -> CommercialOnboarding) para verificar que:
 * 1) un solo persist(user) basta (cascade a través de los dos saltos, sin
 *    TransientPropertyValueException por referencias circulares/no guardadas), y
 * 2) tras recargar desde BD, CommercialDetails.onboarding resuelve al mismo
 *    CommercialOnboarding (de eso depende onboardingStatus en /commercials/initialData).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:commercial-onboarding-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CommercialDetails <-> CommercialOnboarding (cascade de persistencia)")
class CommercialOnboardingCascadeTest {

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("persist(user) cascada hasta CommercialOnboarding sin TransientPropertyValueException")
    void cascadePersistsOnboardingThroughUserAndDetails() {
        User user = new User();
        user.setEmail("comercial-cascade@test.com");
        user.setPhoneNumber("3000000000");
        user.setPassword("hash");
        user.setRole(Role.COMMERCIAL);
        user.setUserState(UserState.PENDING_EMAIL);
        user.setRegisteredDate(ZonedDateTime.now());

        CommercialDetails details = new CommercialDetails();
        details.setUser(user);
        user.setUserDetails(details);

        CommercialOnboarding onboarding = new CommercialOnboarding();
        onboarding.setCommercialDetails(details);
        details.setOnboarding(onboarding);

        em.persist(user);
        em.flush();

        assertThat(details.getId()).isNotNull();
        assertThat(onboarding.getId()).isNotNull();

        Long detailsId = details.getId();
        em.clear();

        CommercialDetails reloaded = em.find(CommercialDetails.class, detailsId);
        assertThat(reloaded.getOnboarding()).isNotNull();
        assertThat(reloaded.getOnboarding().getCurrentStep()).isEqualTo(OnboardingStep.TERMS_PENDING);
    }
}
