package com.verygana2.services.finance;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;
import com.verygana2.exceptions.financeExceptions.InvalidCashRefundStateException;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.finance.PurchaseItemCashRefund.BankAccountType;
import com.verygana2.models.finance.PurchaseItemCashRefund.DocType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.finance.PurchaseItemCashRefundRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.repositories.pqrs.PqrsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link CashRefundServiceImpl}: el flujo de reembolso manual en
 * efectivo (el comprador indica su cuenta, el admin confirma que ya
 * transfirió por fuera de la app).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CashRefundServiceImpl")
class CashRefundServiceImplTest {

    @Mock private PurchaseItemCashRefundRepository purchaseItemCashRefundRepository;
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private AdminDetailsRepository adminDetailsRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private PqrsRepository pqrsRepository;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private RequesterNameResolver requesterNameResolver;

    private CashRefundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CashRefundServiceImpl(purchaseItemCashRefundRepository, purchaseItemRepository,
                adminDetailsRepository, treasuryService, pqrsRepository, emailService, notificationService,
                requesterNameResolver);
    }

    private PurchaseItemCashRefund pendingRefund(Long consumerId, long amountCents) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(consumerId);
        Purchase purchase = Purchase.builder().consumer(consumer).build();
        PurchaseItem item = new PurchaseItem();
        item.setId(5L);
        item.setPurchase(purchase);
        item.setStatus(PurchaseItemStatus.IN_REVIEW);

        return PurchaseItemCashRefund.builder()
                .id(UUID.randomUUID())
                .purchaseItem(item)
                .amountCents(amountCents)
                .status(CashRefundStatus.PENDING_PAYMENT)
                .build();
    }

    private SubmitCashRefundBankDetailsRequestDTO bankDetailsRequest() {
        SubmitCashRefundBankDetailsRequestDTO dto = new SubmitCashRefundBankDetailsRequestDTO();
        dto.setAccountHolderName("Juan Pérez");
        dto.setAccountHolderDoc("123456");
        dto.setAccountHolderDocType(DocType.CC);
        dto.setBankName("Bancolombia");
        dto.setAccountNumber("9988776655");
        dto.setAccountType(BankAccountType.SAVINGS);
        return dto;
    }

    @Nested
    @DisplayName("submitBankDetails")
    class SubmitBankDetails {

        @Test
        @DisplayName("reembolso pendiente del comprador dueño: guarda los datos bancarios")
        void ownedPending_savesBankDetails() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.of(refund));

            service.submitBankDetails(5L, 9L, bankDetailsRequest());

            assertThat(refund.hasBankDetails()).isTrue();
            assertThat(refund.getAccountNumber()).isEqualTo("9988776655");
            verify(purchaseItemCashRefundRepository).save(refund);
        }

        @Test
        @DisplayName("no existe reembolso para ese ítem: lanza EntityNotFoundException")
        void noRefund_throwsEntityNotFoundException() {
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitBankDetails(5L, 9L, bankDetailsRequest()))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("el reembolso no pertenece a ese comprador: lanza EntityNotFoundException (oculta la existencia)")
        void notOwned_throwsEntityNotFoundException() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> service.submitBankDetails(5L, 999L, bankDetailsRequest()))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("reembolso ya pagado: lanza InvalidCashRefundStateException")
        void alreadyPaid_throwsInvalidCashRefundStateException() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.setStatus(CashRefundStatus.PAID);
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> service.submitBankDetails(5L, 9L, bankDetailsRequest()))
                    .isInstanceOf(InvalidCashRefundStateException.class);
        }
    }

    @Nested
    @DisplayName("markPaid")
    class MarkPaid {

        @Test
        @DisplayName("reembolso con datos bancarios: registra el pago en tesorería y marca PAID con el admin")
        void withBankDetails_registersTreasuryAndMarksPaid() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.submitBankDetails("Juan Pérez", "123456", DocType.CC, "Bancolombia", "9988776655",
                    BankAccountType.SAVINGS);
            AdminDetails admin = new AdminDetails();

            when(purchaseItemCashRefundRepository.findById(refund.getId())).thenReturn(Optional.of(refund));
            when(adminDetailsRepository.findById(99L)).thenReturn(Optional.of(admin));

            service.markPaid(refund.getId(), 99L);

            verify(treasuryService).registerManualCashRefundPaid(30_000L, refund.getId());
            assertThat(refund.getStatus()).isEqualTo(CashRefundStatus.PAID);
            assertThat(refund.getPaidByAdmin()).isSameAs(admin);
            verify(purchaseItemCashRefundRepository).save(refund);
        }

        @Test
        @DisplayName("sin datos bancarios todavía: lanza InvalidCashRefundStateException sin tocar tesorería")
        void withoutBankDetails_throwsWithoutTouchingTreasury() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            when(purchaseItemCashRefundRepository.findById(refund.getId())).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> service.markPaid(refund.getId(), 99L))
                    .isInstanceOf(InvalidCashRefundStateException.class);

            verify(treasuryService, never()).registerManualCashRefundPaid(any(), any());
        }

        @Test
        @DisplayName("ya estaba PAID: idempotente, no reprocesa")
        void alreadyPaid_isIdempotent() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.setStatus(CashRefundStatus.PAID);
            when(purchaseItemCashRefundRepository.findById(refund.getId())).thenReturn(Optional.of(refund));

            service.markPaid(refund.getId(), 99L);

            verify(treasuryService, never()).registerManualCashRefundPaid(any(), any());
            verify(purchaseItemCashRefundRepository, never()).save(any());
        }

        @Test
        @DisplayName("reembolso inexistente: lanza EntityNotFoundException")
        void notFound_throwsEntityNotFoundException() {
            UUID id = UUID.randomUUID();
            when(purchaseItemCashRefundRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markPaid(id, 99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("sin PQRS vinculado: marca PAID, no toca el status del ítem ni el PQRS, y no notifica resolución")
        void withoutLinkedPqrs_doesNotTouchPqrs() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.submitBankDetails("Juan Pérez", "123456", DocType.CC, "Bancolombia", "9988776655",
                    BankAccountType.SAVINGS);
            AdminDetails admin = new AdminDetails();

            when(purchaseItemCashRefundRepository.findById(refund.getId())).thenReturn(Optional.of(refund));
            when(adminDetailsRepository.findById(99L)).thenReturn(Optional.of(admin));

            service.markPaid(refund.getId(), 99L);

            // Sin PQRS vinculado (ej. vencimiento automático) no hay ítem en revisión
            // que restaurar — este flujo no toca PurchaseItem.status.
            assertThat(refund.getPurchaseItem().getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
            verifyNoInteractions(purchaseItemRepository, pqrsRepository, emailService, notificationService);
        }

        @Test
        @DisplayName("con PQRS vinculado: marca el ítem REFUNDED, resuelve ese PQRS y notifica al solicitante")
        void withLinkedPqrs_resolvesItAndNotifies() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.submitBankDetails("Juan Pérez", "123456", DocType.CC, "Bancolombia", "9988776655",
                    BankAccountType.SAVINGS);
            AdminDetails admin = new AdminDetails();

            User requester = new User();
            requester.setId(1L);
            requester.setEmail("consumidor@test.com");
            Pqrs linkedPqrs = Pqrs.builder().id(3L).requester(requester)
                    .status(PqrsStatus.PENDIENTE_PAGO_REEMBOLSO).response("Confirmado").build();
            refund.setPqrs(linkedPqrs);

            when(purchaseItemCashRefundRepository.findById(refund.getId())).thenReturn(Optional.of(refund));
            when(adminDetailsRepository.findById(99L)).thenReturn(Optional.of(admin));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requesterNameResolver.resolve(requester)).thenReturn("Juan Pérez");

            service.markPaid(refund.getId(), 99L);

            // Único punto donde este ítem llega a REFUNDED (ver PurchaseItemRefundServiceImpl.refund).
            assertThat(refund.getPurchaseItem().getStatus()).isEqualTo(PurchaseItemStatus.REFUNDED);
            verify(purchaseItemRepository).save(refund.getPurchaseItem());

            assertThat(linkedPqrs.getStatus()).isEqualTo(PqrsStatus.RESUELTA);
            assertThat(linkedPqrs.getResolvedAt()).isNotNull();
            verify(pqrsRepository).save(linkedPqrs);
            verify(notificationService).createInternalNotification(eq(1L), anyString(), anyString(), any());
            verify(emailService).sendPqrsResolved(eq("consumidor@test.com"), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("findByPurchaseItemId")
    class FindByPurchaseItemId {

        @Test
        @DisplayName("existe reembolso para ese ítem: lo devuelve mapeado")
        void found_returnsMappedDto() {
            PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
            refund.submitBankDetails("Juan Pérez", "123456", DocType.CC, "Bancolombia", "9988776655",
                    BankAccountType.SAVINGS);
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.of(refund));

            Optional<CashRefundResponseDTO> result = service.findByPurchaseItemId(5L);

            assertThat(result).isPresent();
            assertThat(result.get().getAccountNumber()).isEqualTo("9988776655");
            assertThat(result.get().getPurchaseItemId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("no hay reembolso para ese ítem: devuelve vacío")
        void notFound_returnsEmpty() {
            when(purchaseItemCashRefundRepository.findByPurchaseItemId(5L)).thenReturn(Optional.empty());

            assertThat(service.findByPurchaseItemId(5L)).isEmpty();
        }
    }

    @Test
    @DisplayName("getRefunds: delega en el repositorio filtrando por status y rango de fechas de creacion")
    void getRefunds_delegatesToRepository() {
        PurchaseItemCashRefund refund = pendingRefund(9L, 30_000L);
        Pageable pageable = Pageable.ofSize(20);
        Page<PurchaseItemCashRefund> page = new PageImpl<>(java.util.List.of(refund));
        ZonedDateTime startDate = ZonedDateTime.now().minusDays(7);
        ZonedDateTime endDate = ZonedDateTime.now();

        when(purchaseItemCashRefundRepository.findByStatusAndRangeDates(CashRefundStatus.PENDING_PAYMENT, startDate,
                endDate, pageable)).thenReturn(page);

        var result = service.getRefunds(CashRefundStatus.PENDING_PAYMENT, startDate, endDate, pageable);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getAmountCents()).isEqualTo(30_000L);
    }
}
