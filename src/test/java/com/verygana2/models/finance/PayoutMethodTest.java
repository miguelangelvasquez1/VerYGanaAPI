package com.verygana2.models.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.finance.PayoutMethod.VerificationStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link PayoutMethod}: solo un método VERIFIED y activo
 * puede recibir payouts, y las transiciones de verificación/rechazo.
 */
@DisplayName("PayoutMethod (entidad)")
class PayoutMethodTest {

    @Test
    @DisplayName("canBeUsedForPayout: true solo si está VERIFIED y activo")
    void canBeUsedForPayout_requiresVerifiedAndActive() {
        PayoutMethod method = PayoutMethod.builder()
                .verificationStatus(VerificationStatus.VERIFIED).active(true).build();
        assertThat(method.canBeUsedForPayout()).isTrue();

        method.setActive(false);
        assertThat(method.canBeUsedForPayout()).isFalse();

        method.setActive(true);
        method.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        assertThat(method.canBeUsedForPayout()).isFalse();
    }

    @Test
    @DisplayName("markVerified: pasa a VERIFIED, marca la fecha y limpia el motivo de rechazo previo")
    void markVerified_setsStatusAndClearsRejectionReason() {
        PayoutMethod method = PayoutMethod.builder()
                .verificationStatus(VerificationStatus.UNDER_REVIEW)
                .rejectionReason("dato inválido")
                .build();

        method.markVerified();

        assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(method.getVerifiedAt()).isNotNull();
        assertThat(method.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("reject: pasa a REJECTED y guarda el motivo")
    void reject_setsStatusAndReason() {
        PayoutMethod method = PayoutMethod.builder().verificationStatus(VerificationStatus.UNDER_REVIEW).build();

        method.reject("Documento no coincide con el titular");

        assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(method.getRejectionReason()).isEqualTo("Documento no coincide con el titular");
    }

    @Test
    @DisplayName("markFirstPayoutCompleted: activa la bandera de retención antifraude ya cumplida")
    void markFirstPayoutCompleted_setsFlag() {
        PayoutMethod method = PayoutMethod.builder().firstPayoutCompleted(false).build();

        method.markFirstPayoutCompleted();

        assertThat(method.isFirstPayoutCompleted()).isTrue();
    }
}
