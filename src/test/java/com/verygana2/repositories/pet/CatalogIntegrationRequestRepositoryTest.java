
package com.verygana2.repositories.pet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para CatalogIntegrationRequestRepository.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:catalog-request-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CatalogIntegrationRequestRepository (integración H2)")
class CatalogIntegrationRequestRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CatalogIntegrationRequestRepository repository;

    private void persistRequest(CommercialDetails commercial, CatalogRequestStatus status,
                                Long budgetCents, long spentCents) {
        CatalogIntegrationRequest request = new CatalogIntegrationRequest();
        request.setCommercial(commercial);
        request.setProductName("Producto");
        request.setDescription("Descripción");
        request.setDesiredEffects("Efectos");
        request.setStatus(status);
        request.setBudgetCents(budgetCents);
        request.setSpentCents(spentCents);
        em.persist(request);
    }

    @Test
    @DisplayName("suma lo que queda de las bolsas vivas, sin rechazadas ni solicitudes sin bolsa")
    void sumsUnspentBudgetOfLiveRequests() {
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        persistRequest(commercial, CatalogRequestStatus.COMPLETED, 30_000L, 15_000L);
        persistRequest(commercial, CatalogRequestStatus.PENDING, 45_000L, 0L);
        // Rechazada: la bolsa volvió entera a la wallet.
        persistRequest(commercial, CatalogRequestStatus.REJECTED, 60_000L, 0L);
        // Anterior al cobro: nunca reservó bolsa.
        persistRequest(commercial, CatalogRequestStatus.COMPLETED, null, 0L);
        em.flush();

        assertThat(repository.sumCommittedUnspentBudgetCents()).isEqualTo(15_000L + 45_000L);
    }

    @Test
    @DisplayName("sin solicitudes con bolsa devuelve cero, no null")
    void emptyIsZero() {
        assertThat(repository.sumCommittedUnspentBudgetCents()).isZero();
    }
}
