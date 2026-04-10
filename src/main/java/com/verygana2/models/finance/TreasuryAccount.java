package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.enums.finance.TreasuryAccountCode;

@Entity
@Table(
    name = "treasury_accounts",
    uniqueConstraints = @UniqueConstraint(columnNames = "code")
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TreasuryAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Identificador funcional fijo. Solo existen 4 registros en toda la vida
     * de la aplicación: KEYS_RESERVE, FORTIFICATION, OPERATIONS, PAYOUTS_PENDING.
     * Es UNIQUE para evitar duplicados y se usa en código en lugar del id numérico.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, updatable = false, length = 32)
    @NotNull
    private TreasuryAccountCode code;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String name;

    /**
     * Saldo en centavos de COP (sin decimales).
     * Se usa Long porque los centavos son enteros y es más eficiente
     * que BigDecimal para sumas frecuentes. Jamás puede ser negativo.
     */
    @Column(name = "balance_cents", nullable = false)
    @Builder.Default
    private Long balanceCents = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.balanceCents == null) this.balanceCents = 0L;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}