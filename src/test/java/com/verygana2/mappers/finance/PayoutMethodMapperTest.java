package com.verygana2.mappers.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.BankAccountType;
import com.verygana2.models.finance.PayoutMethod.DocType;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.userDetails.CommercialDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link PayoutMethodMapper}: confirma exactamente qué campos ignora
 * el mapper al crear un {@link PayoutMethod} desde el request (quedan en su
 * valor por defecto de la entidad, no en null "a secas" cuando el campo tiene
 * {@code @Builder.Default}), y que el DTO de respuesta deja
 * {@code defaultMethod}/{@code certificateUrl} sin resolver porque eso se
 * completa en el service.
 */
@DisplayName("PayoutMethodMapper")
class PayoutMethodMapperTest {

    private final PayoutMethodMapper mapper = new PayoutMethodMapperImpl();

    private CreatePayoutMethodRequestDTO fullRequest() {
        CreatePayoutMethodRequestDTO request = new CreatePayoutMethodRequestDTO();
        request.setType(PayoutMethodType.BANK_ACCOUNT);
        request.setAlias("Cuenta Bancolombia principal");
        request.setBankCode("bank-uuid-123");
        request.setAccountNumber("1234567890");
        request.setBankAccountType(BankAccountType.SAVINGS);
        request.setPhoneNumber("3001234567");
        request.setAccountHolderName("Juan Pérez");
        request.setAccountHolderDoc("123456789");
        request.setAccountHolderDocType(DocType.CC);
        return request;
    }

    @Nested
    @DisplayName("toPayoutMethod(CreatePayoutMethodRequestDTO)")
    class ToPayoutMethod {

        @Test
        @DisplayName("copia los campos NO ignorados: type, alias, bankCode, accountNumber, bankAccountType, "
                + "phoneNumber, accountHolderName, accountHolderDoc, accountHolderDocType")
        void copiesNonIgnoredFields() {
            PayoutMethod result = mapper.toPayoutMethod(fullRequest());

            assertThat(result.getType()).isEqualTo(PayoutMethodType.BANK_ACCOUNT);
            assertThat(result.getAlias()).isEqualTo("Cuenta Bancolombia principal");
            assertThat(result.getBankCode()).isEqualTo("bank-uuid-123");
            assertThat(result.getAccountNumber()).isEqualTo("1234567890");
            assertThat(result.getBankAccountType()).isEqualTo(BankAccountType.SAVINGS);
            assertThat(result.getPhoneNumber()).isEqualTo("3001234567");
            assertThat(result.getAccountHolderName()).isEqualTo("Juan Pérez");
            assertThat(result.getAccountHolderDoc()).isEqualTo("123456789");
            assertThat(result.getAccountHolderDocType()).isEqualTo(DocType.CC);
        }

        @Test
        @DisplayName("id, commercial, rejectionReason, createdAt, verifiedAt y certificateAsset quedan en null (ignorados, sin default de entidad)")
        void ignoredFieldsWithoutEntityDefault_areNull() {
            PayoutMethod result = mapper.toPayoutMethod(fullRequest());

            assertThat(result.getId()).isNull();
            assertThat(result.getCommercial()).isNull();
            assertThat(result.getRejectionReason()).isNull();
            assertThat(result.getCreatedAt()).isNull();
            assertThat(result.getVerifiedAt()).isNull();
            assertThat(result.getCertificateAsset()).isNull();
        }

        @Test
        @DisplayName("verificationStatus, active y firstPayoutCompleted quedan en el @Builder.Default de la entidad "
                + "(PENDING_VERIFICATION / true / false), NO en null: el mapper no los setea, "
                + "así que el builder de PayoutMethod usa sus valores por defecto declarados")
        void ignoredFieldsWithEntityDefault_useBuilderDefaults() {
            PayoutMethod result = mapper.toPayoutMethod(fullRequest());

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);
            assertThat(result.isActive()).isTrue();
            assertThat(result.isFirstPayoutCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toPayoutMethodResponseDTO(PayoutMethod)")
    class ToPayoutMethodResponseDTO {

        @Test
        @DisplayName("defaultMethod y certificateUrl quedan sin setear (ignorados; se resuelven en el service)")
        void defaultMethodAndCertificateUrl_areIgnored() {
            PayoutMethod payoutMethod = PayoutMethod.builder()
                    .id(1L)
                    .commercial(new CommercialDetails())
                    .type(PayoutMethodType.NEQUI)
                    .alias("Nequi personal")
                    .phoneNumber("3001234567")
                    .accountHolderName("Juan Pérez")
                    .accountHolderDoc("123456789")
                    .accountHolderDocType(DocType.CC)
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .active(true)
                    .firstPayoutCompleted(true)
                    .build();

            PayoutMethodResponseDTO dto = mapper.toPayoutMethodResponseDTO(payoutMethod);

            assertThat(dto.isDefaultMethod()).isFalse();
            assertThat(dto.getCertificateUrl()).isNull();
        }

        @Test
        @DisplayName("copia el resto de campos desde la entidad")
        void copiesRemainingFields() {
            PayoutMethod payoutMethod = PayoutMethod.builder()
                    .id(1L)
                    .type(PayoutMethodType.BANK_ACCOUNT)
                    .alias("Cuenta principal")
                    .bankCode("bank-uuid-123")
                    .accountNumber("1234567890")
                    .bankAccountType(BankAccountType.CHECKING)
                    .accountHolderName("Juan Pérez")
                    .accountHolderDoc("123456789")
                    .accountHolderDocType(DocType.CC)
                    .verificationStatus(VerificationStatus.UNDER_REVIEW)
                    .rejectionReason(null)
                    .active(true)
                    .firstPayoutCompleted(false)
                    .build();

            PayoutMethodResponseDTO dto = mapper.toPayoutMethodResponseDTO(payoutMethod);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getType()).isEqualTo(PayoutMethodType.BANK_ACCOUNT);
            assertThat(dto.getAlias()).isEqualTo("Cuenta principal");
            assertThat(dto.getBankCode()).isEqualTo("bank-uuid-123");
            assertThat(dto.getAccountNumber()).isEqualTo("1234567890");
            assertThat(dto.getBankAccountType()).isEqualTo(BankAccountType.CHECKING);
            assertThat(dto.getAccountHolderName()).isEqualTo("Juan Pérez");
            assertThat(dto.getAccountHolderDoc()).isEqualTo("123456789");
            assertThat(dto.getAccountHolderDocType()).isEqualTo(DocType.CC);
            assertThat(dto.getVerificationStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.isFirstPayoutCompleted()).isFalse();
        }
    }
}
