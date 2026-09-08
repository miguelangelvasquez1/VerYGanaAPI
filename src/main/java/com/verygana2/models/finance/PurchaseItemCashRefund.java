package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.AdminDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reembolso en efectivo pendiente de pago manual: la porción en efectivo de
 * un PurchaseItem reembolsado (ver PurchaseItemRefundService) que VerYGana
 * paga por fuera de la app —transferencia bancaria propia— porque Wompi no
 * documenta un endpoint de reverso de cargos. El dinero ya salió de
 * PAYOUTS_PENDING hacia OPERATIONS en el momento en que se crea este
 * registro (ver TreasuryService.reversePurchaseItemForRefund); cuando el
 * admin confirma el pago, sale de OPERATIONS hacia afuera
 * (TreasuryService.registerManualCashRefundPaid).
 */
@Entity
@Table(name = "purchase_item_cash_refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseItemCashRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id", nullable = false, unique = true)
    private PurchaseItem purchaseItem;

    /**
     * Nullable: solo se llena cuando este reembolso se originó al resolver un
     * PQRS con action=REFUND (ver PqrsServiceImpl.respondToPqrs). Un
     * vencimiento automático (PurchaseItemExpirationScheduler.expireUnclaimed)
     * no tiene PQRS asociado. Cuando el admin marca este reembolso como PAID
     * (CashRefundService.markPaid), si hay un PQRS vinculado se resuelve en
     * ese mismo momento — todo el flujo queda dentro del mismo PQRS.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pqrs_id")
    private Pqrs pqrs;

    /** Porción en efectivo a reembolsar (excluye lo que ya se acreditó como llaves). */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    // ===== Datos bancarios (null hasta que el comprador los indique) =====

    @Column(name = "account_holder_name", length = 200)
    private String accountHolderName;

    @Column(name = "account_holder_doc", length = 20)
    private String accountHolderDoc;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_holder_doc_type", length = 10)
    private DocType accountHolderDocType;

    /** Nombre del banco o billetera (texto libre — no hay integración con un catálogo, el pago es manual). */
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private BankAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CashRefundStatus status = CashRefundStatus.PENDING_PAYMENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "bank_details_submitted_at")
    private ZonedDateTime bankDetailsSubmittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_admin_id")
    private AdminDetails paidByAdmin;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public boolean hasBankDetails() {
        return accountNumber != null && !accountNumber.isBlank();
    }

    public void submitBankDetails(String accountHolderName, String accountHolderDoc, DocType accountHolderDocType,
            String bankName, String accountNumber, BankAccountType accountType) {
        this.accountHolderName = accountHolderName;
        this.accountHolderDoc = accountHolderDoc;
        this.accountHolderDocType = accountHolderDocType;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.bankDetailsSubmittedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void markPaid(AdminDetails admin) {
        this.status = CashRefundStatus.PAID;
        this.paidByAdmin = admin;
        this.paidAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public enum DocType {
        CC, CE, NIT, PP, TI
    }

    public enum BankAccountType {
        SAVINGS, CHECKING
    }
}
