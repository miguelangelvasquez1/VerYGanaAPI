package com.verygana2.models.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private CommercialDetails commercial;

    @ManyToOne(optional = false)
    private Plan plan;

    // ───── Dinero ─────

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal investmentAmount; // total inicial

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal remainingAmount; // saldo actual

    @Column(nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal recoveredAmount = BigDecimal.ZERO; // Total recuperado (ventas hechas)

    // ───── ROI ─────

    @Column(nullable = false)
    @Builder.Default
    private boolean roiReached = false;

    private ZonedDateTime roiReachedAt;

    // ───── Estado ─────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvestmentStatus status = InvestmentStatus.ACTIVE;

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    private ZonedDateTime exhaustedAt;
    private ZonedDateTime closedAt;

    public enum InvestmentStatus {
        ACTIVE,
        EXHAUSTED,
        CLOSED
    }

    // ───── Lógica ─────

    public boolean hasFunds() {
        return status == InvestmentStatus.ACTIVE &&
               remainingAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public void consume(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto inválido");
        }

        if (remainingAmount.compareTo(amount) < 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }

        remainingAmount = remainingAmount.subtract(amount);

        if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            status = InvestmentStatus.EXHAUSTED;
            exhaustedAt = ZonedDateTime.now();
        }
    }

    public void addRevenue(BigDecimal amount) {
        recoveredAmount = recoveredAmount.add(amount);

        if (!roiReached && reachedRoi()) {
            roiReached = true;
            roiReachedAt = ZonedDateTime.now();
        }
    }

    public void closeInvestment() {
        status = InvestmentStatus.CLOSED;
        closedAt = ZonedDateTime.now();
    }

    private boolean reachedRoi() {
        return recoveredAmount.compareTo(
            investmentAmount.multiply(BigDecimal.valueOf(6))
        ) >= 0;
    }

    /**
     * Devuelve fondos al saldo disponible de la inversión.
     * Inverso de consume().
     */
    public void refund(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a devolver debe ser positivo");
        }
        this.remainingAmount = this.remainingAmount.add(amount);
    }
}