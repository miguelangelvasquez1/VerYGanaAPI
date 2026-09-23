package com.verygana2.services.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.exceptions.RegistrationRejectedException;
import com.verygana2.models.enums.RegistrationRejectionReason;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.repositories.legal.LegalDocumentRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumerEligibilityServiceImpl.assertEligible")
class ConsumerEligibilityServiceImplTest {

    private static final String ACTIVE_TERMS_VERSION = "1";
    private static final LocalDate TODAY_BOGOTA = LocalDate.of(2026, 5, 10);

    @Mock LegalDocumentRepository legalDocumentRepository;

    /** "Hoy" en Bogotá = bogotaDate, sin importar la fecha real del sistema al correr el test. */
    private Clock clockFor(LocalDate bogotaDate) {
        // 15:00 UTC = 10:00 Bogotá (UTC-5): bien adentro del mismo día calendario en Bogotá.
        return Clock.fixed(LocalDateTime.of(bogotaDate, java.time.LocalTime.of(15, 0)).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private ConsumerEligibilityServiceImpl serviceWith(Clock clock) {
        return new ConsumerEligibilityServiceImpl(clock, legalDocumentRepository);
    }

    private ConsumerRegisterDTO dto(LocalDate birthDate, boolean ageDeclaration, boolean termsAccepted, String termsVersion) {
        ConsumerRegisterDTO dto = new ConsumerRegisterDTO();
        dto.setBirthDate(birthDate);
        dto.setAgeDeclaration(ageDeclaration);
        dto.setTermsAccepted(termsAccepted);
        dto.setTermsVersion(termsVersion);
        return dto;
    }

    private void mockActiveTerms() {
        LegalDocument activeTerms = new LegalDocument();
        activeTerms.setVersion(ACTIVE_TERMS_VERSION);
        when(legalDocumentRepository.findByTypeAndActiveTrue(LegalDocumentType.USERS_TERMS_AND_CONDITIONS))
                .thenReturn(Optional.of(activeTerms));
    }

    @Test
    @DisplayName("adulto con declaración y términos vigentes: no lanza nada")
    void adultIsAccepted() {
        mockActiveTerms();
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(30), true, true, ACTIVE_TERMS_VERSION);

        assertThatCode(() -> service.assertEligible(dto)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("menor rechazado con MINOR_AGE aunque declare ser mayor de edad")
    void minorIsRejectedRegardlessOfAgeDeclaration() {
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(17), true, true, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.MINOR_AGE));
    }

    @Test
    @DisplayName("cumple 18 hoy (zona Bogotá): acepta")
    void turns18Today() {
        mockActiveTerms();
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(18), true, true, ACTIVE_TERMS_VERSION);

        assertThatCode(() -> service.assertEligible(dto)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cumple 18 mañana: rechaza con MINOR_AGE")
    void turns18Tomorrow() {
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(18).plusDays(1), true, true, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.MINOR_AGE));
    }

    @Test
    @DisplayName("nacido 29 de febrero: el 28 de febrero del año en que cumple 18 todavía es menor")
    void leapDayBirthday_dayBefore_isMinor() {
        LocalDate birthDate = LocalDate.of(2008, 2, 29);
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(LocalDate.of(2026, 2, 28)));
        ConsumerRegisterDTO dto = dto(birthDate, true, true, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.MINOR_AGE));
    }

    @Test
    @DisplayName("nacido 29 de febrero: el 1 de marzo del año en que cumple 18 ya es mayor")
    void leapDayBirthday_dayAfter_isAdult() {
        mockActiveTerms();
        LocalDate birthDate = LocalDate.of(2008, 2, 29);
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(LocalDate.of(2026, 3, 1)));
        ConsumerRegisterDTO dto = dto(birthDate, true, true, ACTIVE_TERMS_VERSION);

        assertThatCode(() -> service.assertEligible(dto)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("registro a las 11pm hora Colombia (ya es otro día en UTC): usa la fecha de Bogotá, no la de UTC")
    void registrationAt11pmBogota_usesBogotaDateNotUtc() {
        // 2026-03-15T04:30:00Z = 2026-03-14 23:30 en Bogotá (UTC-5): en Bogotá todavía es 14 de marzo,
        // un día antes de que cumpla 18 (nace 15 de marzo). Con UTC crudo daría 18 (bug); con Bogotá da 17.
        Clock elevenPmBogota = Clock.fixed(java.time.Instant.parse("2026-03-15T04:30:00Z"), ZoneOffset.UTC);
        LocalDate birthDate = LocalDate.of(2008, 3, 15);
        ConsumerEligibilityServiceImpl service = serviceWith(elevenPmBogota);
        ConsumerRegisterDTO dto = dto(birthDate, true, true, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.MINOR_AGE));
    }

    @Test
    @DisplayName("sin declaración de edad: AGE_DECLARATION_MISSING")
    void missingAgeDeclaration() {
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(30), false, true, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.AGE_DECLARATION_MISSING));
    }

    @Test
    @DisplayName("términos no aceptados: TERMS_NOT_ACCEPTED")
    void termsNotAccepted() {
        mockActiveTerms();
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(30), true, false, ACTIVE_TERMS_VERSION);

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.TERMS_NOT_ACCEPTED));
    }

    @Test
    @DisplayName("versión de términos desactualizada: TERMS_NOT_ACCEPTED")
    void staleTermsVersionRejected() {
        mockActiveTerms();
        ConsumerEligibilityServiceImpl service = serviceWith(clockFor(TODAY_BOGOTA));
        ConsumerRegisterDTO dto = dto(TODAY_BOGOTA.minusYears(30), true, true, "0-vieja");

        assertThatThrownBy(() -> service.assertEligible(dto))
                .isInstanceOf(RegistrationRejectedException.class)
                .satisfies(ex -> assertThat(((RegistrationRejectedException) ex).getReason())
                        .isEqualTo(RegistrationRejectionReason.TERMS_NOT_ACCEPTED));
    }
}
