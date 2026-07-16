package com.verygana2.referrals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.mappers.ReferralMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.referrals.ReferralServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralServiceImpl")
class ReferralServiceImplTest {

    @Mock ConsumerDetailsRepository consumerDetailsRepository;
    @Mock ReferralMapper referralMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks ReferralServiceImpl service;

    private User newUser;
    private ConsumerDetails newConsumer;
    private ConsumerDetails referrer;

    @BeforeEach
    void setUp() {
        newUser = new User();
        newUser.setEmail("nuevo@test.com");

        newConsumer = new ConsumerDetails();

        User referrerUser = new User();
        referrerUser.setEmail("referidor@test.com");

        referrer = new ConsumerDetails();
        referrer.setId(7L);
        referrer.setUser(referrerUser);
        referrer.setReferralCode("ABCD1234");
    }

    // ─── generateUniqueCode ───────────────────────────────────────────────────

    @Nested
    @DisplayName("generateUniqueCode")
    class GenerateUniqueCode {

        @Test
        @DisplayName("retorna código en mayúsculas con la longitud pedida")
        void returnsUppercaseCodeOfRequestedLength() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);

            String code = service.generateUniqueCode(8);

            assertThat(code).hasSize(8).isEqualTo(code.toUpperCase());
        }

        @Test
        @DisplayName("lanza IllegalStateException tras 10 colisiones seguidas")
        void throwsAfterTenCollisions() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(true);

            assertThatThrownBy(() -> service.generateUniqueCode(8))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── prepareNewConsumer ───────────────────────────────────────────────────

    @Nested
    @DisplayName("prepareNewConsumer")
    class PrepareNewConsumer {

        @Test
        @DisplayName("sin código de referido: asigna código propio y no publica evento")
        void withoutReferralCodeOnlyAssignsOwnCode() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);

            service.prepareNewConsumer(newUser, newConsumer, null);

            assertThat(newConsumer.getReferralCode()).isNotBlank();
            assertThat(newConsumer.getReferredBy()).isNull();
            verify(eventPublisher, never())
                    .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        }

        @Test
        @DisplayName("código en blanco se trata como sin referido")
        void blankCodeTreatedAsNoReferral() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);

            service.prepareNewConsumer(newUser, newConsumer, "   ");

            assertThat(newConsumer.getReferredBy()).isNull();
            verify(eventPublisher, never())
                    .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        }

        @Test
        @DisplayName("con código válido: asigna referredBy y publica XP REFERRAL_ACTIVE al referidor")
        void validCodeAssignsReferrerAndPublishesXp() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);
            when(consumerDetailsRepository.findByReferralCode("ABCD1234"))
                    .thenReturn(Optional.of(referrer));

            service.prepareNewConsumer(newUser, newConsumer, "abcd1234");

            assertThat(newConsumer.getReferredBy()).isSameAs(referrer);

            ArgumentCaptor<XpAwardRequestedEvent> captor =
                    ArgumentCaptor.forClass(XpAwardRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            XpAwardRequestedEvent event = captor.getValue();
            assertThat(event.getConsumerId()).isEqualTo(referrer.getId());
            assertThat(event.getActivityType()).isEqualTo(ActivityType.REFERRAL_ACTIVE);
        }

        @Test
        @DisplayName("normaliza el código: trim + mayúsculas antes de buscar")
        void normalizesCodeBeforeLookup() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);
            when(consumerDetailsRepository.findByReferralCode("ABCD1234"))
                    .thenReturn(Optional.of(referrer));

            service.prepareNewConsumer(newUser, newConsumer, "  abcd1234  ");

            verify(consumerDetailsRepository).findByReferralCode("ABCD1234");
        }

        @Test
        @DisplayName("código inexistente lanza excepción")
        void invalidCodeThrows() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);
            when(consumerDetailsRepository.findByReferralCode("NOEXISTE"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.prepareNewConsumer(newUser, newConsumer, "noexiste"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("inválido");
        }

        @Test
        @DisplayName("auto-referido (mismo email) lanza excepción y no publica evento")
        void selfReferralThrows() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);
            newUser.setEmail("referidor@test.com"); // mismo email que el dueño del código
            when(consumerDetailsRepository.findByReferralCode("ABCD1234"))
                    .thenReturn(Optional.of(referrer));

            assertThatThrownBy(() ->
                    service.prepareNewConsumer(newUser, newConsumer, "ABCD1234"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("auto-referido");

            verify(eventPublisher, never())
                    .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        }

        @Test
        @DisplayName("no sobrescribe un referidor ya asignado ni publica evento extra")
        void doesNotOverwriteExistingReferrer() {
            when(consumerDetailsRepository.existsByReferralCode(anyString())).thenReturn(false);
            ConsumerDetails originalReferrer = new ConsumerDetails();
            originalReferrer.setId(99L);
            newConsumer.setReferredBy(originalReferrer);
            when(consumerDetailsRepository.findByReferralCode("ABCD1234"))
                    .thenReturn(Optional.of(referrer));

            service.prepareNewConsumer(newUser, newConsumer, "ABCD1234");

            assertThat(newConsumer.getReferredBy()).isSameAs(originalReferrer);
            verify(eventPublisher, never())
                    .publishEvent(any(org.springframework.context.ApplicationEvent.class));
        }
    }

    // ─── getReferralsByEmail ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getReferralsByEmail")
    class GetReferralsByEmail {

        @Test
        @DisplayName("lanza excepción si el email no tiene ConsumerDetails")
        void throwsWhenEmailNotFound() {
            when(consumerDetailsRepository.findByUserEmail("nadie@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getReferralsByEmail("nadie@test.com"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}