package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PayoutMethodCertificateAssetRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payout-method-cert-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PayoutMethodCertificateAssetRepository (integración H2)")
class PayoutMethodCertificateAssetRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PayoutMethodCertificateAssetRepository certificateAssetRepository;

    // ==================== HELPERS ====================

    private PayoutMethodCertificateAsset persistCertificateAsset(PayoutMethod payoutMethod, String objectKey) {
        PayoutMethodCertificateAsset asset = PayoutMethodCertificateAsset.builder()
                .objectKey(objectKey)
                .sizeBytes(2048L)
                .mimeType(SupportedMimeType.APPLICATION_PDF)
                .status(AssetStatus.PENDING)
                .payoutMethod(payoutMethod)
                .uploadedAt(ZonedDateTime.now())
                .build();
        em.persist(asset);
        em.flush();
        return asset;
    }

    // ==================== findByPayoutMethodId ====================

    @Nested
    @DisplayName("findByPayoutMethodId")
    class FindByPayoutMethodId {

        @Test
        @DisplayName("trae la certificación asociada al payout method")
        void returnsCertificateAssetForPayoutMethod() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod payoutMethod = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.UNDER_REVIEW);
            PayoutMethodCertificateAsset asset = persistCertificateAsset(payoutMethod, "certs/certificate-1.pdf");

            Optional<PayoutMethodCertificateAsset> found = certificateAssetRepository
                    .findByPayoutMethodId(payoutMethod.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(asset.getId());
            assertThat(found.get().getObjectKey()).isEqualTo("certs/certificate-1.pdf");
        }

        @Test
        @DisplayName("vacío cuando el payout method no tiene certificación")
        void emptyWhenPayoutMethodHasNoCertificate() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod payoutMethod = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.UNDER_REVIEW);

            Optional<PayoutMethodCertificateAsset> found = certificateAssetRepository
                    .findByPayoutMethodId(payoutMethod.getId());

            assertThat(found).isEmpty();
        }
    }
}
